package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.project

import com.github.hechtcarmel.jetbrainsindexmcpplugin.constants.ParamNames
import com.github.hechtcarmel.jetbrainsindexmcpplugin.constants.ToolNames
import com.github.hechtcarmel.jetbrainsindexmcpplugin.server.ProjectResolver
import com.github.hechtcarmel.jetbrainsindexmcpplugin.server.models.ToolCallResult
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.AbstractMcpTool
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.BuildMessage
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.BuildProjectResult
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.schema.SchemaBuilder
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.task.ProjectTaskContext
import com.intellij.task.ProjectTaskListener
import com.intellij.task.ProjectTaskManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

class BuildProjectTool : AbstractMcpTool() {

    companion object {
        private val LOG = logger<BuildProjectTool>()
        private const val MAX_BUILD_MESSAGES = 200
    }

    override val requiresPsiSync: Boolean = false

    override val name = ToolNames.BUILD_PROJECT

    override val description = """
        Build the project using the IDE's build system (supports JPS, Gradle, Maven).
        Use after making code changes to check for compilation errors.

        Returns: success status, error/warning counts (when available), and structured build messages with file locations.
        Structured error details (file, line, column) are available in Java/Kotlin IDEs. In other IDEs, use the success flag.

        When project_path points to a workspace sub-project, that module and its dependencies are built.

        Parameters: project_path (optional), rebuild (optional, default false), includeRawOutput (optional, default false), timeoutSeconds (optional).

        Example: {} or {"rebuild": true} or {"includeRawOutput": true, "timeoutSeconds": 120}
    """.trimIndent()

    override val inputSchema: JsonObject = SchemaBuilder.tool()
        .projectPath()
        .booleanProperty(ParamNames.REBUILD, "Full rebuild instead of incremental build. Default: false.")
        .booleanProperty(ParamNames.INCLUDE_RAW_OUTPUT, "Include raw build output log in response. Default: false.")
        .intProperty(ParamNames.TIMEOUT_SECONDS, "Timeout in seconds. No timeout if omitted.")
        .build()

    override suspend fun doExecute(project: Project, arguments: JsonObject): ToolCallResult {
        if (!TrustedProjects.isProjectTrusted(project)) {
            return createErrorResult("Cannot build: project is not trusted. Open project settings to mark it as trusted.")
        }

        val rebuild = arguments[ParamNames.REBUILD]?.jsonPrimitive?.booleanOrNull ?: false
        val includeRawOutput = arguments[ParamNames.INCLUDE_RAW_OUTPUT]?.jsonPrimitive?.booleanOrNull ?: false
        val timeoutSeconds = arguments[ParamNames.TIMEOUT_SECONDS]?.jsonPrimitive?.intOrNull

        val startTime = System.currentTimeMillis()

        val projectPathArg = arguments[ParamNames.PROJECT_PATH]?.jsonPrimitive?.content
        val targetModule = resolveTargetModule(project, projectPathArg)

        val sessionId = UUID.randomUUID().toString()
        val context = ProjectTaskContext(sessionId)

        val taskManager = ProjectTaskManager.getInstance(project)
        val isIncremental = !rebuild
        val task = if (targetModule != null) {
            taskManager.createModulesBuildTask(arrayOf(targetModule), isIncremental, true, true, true)
        } else {
            taskManager.createAllModulesBuildTask(isIncremental, project)
        }

        val buildDeferred = CompletableDeferred<ProjectTaskManager.Result>()
        val compilerMessages = mutableListOf<BuildMessage>()
        val rawOutputLines = StringBuilder()

        val connection = project.messageBus.connect()
        connection.subscribe(ProjectTaskListener.TOPIC, object : ProjectTaskListener {
            override fun finished(result: ProjectTaskManager.Result) {
                if (result.context.sessionId == sessionId) {
                    buildDeferred.complete(result)
                }
            }
        })

        subscribeToCompilerMessages(project, connection, compilerMessages, rawOutputLines, includeRawOutput)

        try {
            val promise = taskManager.run(context, task)

            promise.onError { error ->
                if (!buildDeferred.isCompleted) {
                    buildDeferred.completeExceptionally(error)
                }
            }

            val result = if (timeoutSeconds != null) {
                withTimeoutOrNull(timeoutSeconds * 1000L) {
                    buildDeferred.await()
                }
            } else {
                buildDeferred.await()
            }

            val durationMs = System.currentTimeMillis() - startTime

            if (result == null) {
                return createJsonResult(BuildProjectResult(
                    success = false,
                    aborted = true,
                    errors = null,
                    warnings = null,
                    buildMessages = emptyList(),
                    durationMs = durationMs
                ))
            }

            val truncated = compilerMessages.size > MAX_BUILD_MESSAGES
            val messages = if (truncated) compilerMessages.take(MAX_BUILD_MESSAGES) else compilerMessages
            val errorCount = messages.count { it.category == "ERROR" }.takeIf { compilerMessages.isNotEmpty() }
            val warningCount = messages.count { it.category == "WARNING" }.takeIf { compilerMessages.isNotEmpty() }

            return createJsonResult(BuildProjectResult(
                success = !result.hasErrors() && !result.isAborted,
                aborted = result.isAborted,
                errors = errorCount,
                warnings = warningCount,
                buildMessages = messages,
                truncated = truncated,
                rawOutput = if (includeRawOutput && rawOutputLines.isNotEmpty()) rawOutputLines.toString() else null,
                durationMs = durationMs
            ))
        } catch (e: Exception) {
            LOG.warn("Build failed with exception", e)
            return createErrorResult("Build failed: ${e.message}")
        } finally {
            connection.disconnect()
        }
    }

    private fun resolveTargetModule(project: Project, projectPathArg: String?): Module? {
        if (projectPathArg == null) return null

        val normalizedPath = ProjectResolver.normalizePath(projectPathArg)
        val projectBasePath = project.basePath?.let { ProjectResolver.normalizePath(it) }

        if (normalizedPath == projectBasePath) return null

        try {
            val modules = ModuleManager.getInstance(project).modules
            for (module in modules) {
                val contentRoots = ModuleRootManager.getInstance(module).contentRoots
                for (root in contentRoots) {
                    val rootPath = ProjectResolver.normalizePath(root.path)
                    if (normalizedPath == rootPath) return module
                    if (normalizedPath.startsWith("$rootPath/")) return module
                }
            }
        } catch (e: Exception) {
            LOG.debug("Failed to resolve target module from project_path", e)
        }

        return null
    }

    @Suppress("UNCHECKED_CAST")
    private fun subscribeToCompilerMessages(
        project: Project,
        connection: com.intellij.util.messages.MessageBusConnection,
        messages: MutableList<BuildMessage>,
        rawOutput: StringBuilder,
        includeRawOutput: Boolean
    ) {
        try {
            val compilerTopicsClass = Class.forName("com.intellij.openapi.compiler.CompilerTopics")
            val compilationStatusField = compilerTopicsClass.getField("COMPILATION_STATUS")
            val topic = compilationStatusField.get(null) as com.intellij.util.messages.Topic<Any>

            val listenerClass = Class.forName("com.intellij.openapi.compiler.CompilationStatusListener")
            val proxy = java.lang.reflect.Proxy.newProxyInstance(
                listenerClass.classLoader,
                arrayOf(listenerClass)
            ) { _, method, args ->
                if (method.name == "compilationFinished" && args != null && args.size >= 3) {
                    val compileContext = args.getOrNull(3) ?: return@newProxyInstance null
                    extractCompilerMessages(compileContext, messages, rawOutput, includeRawOutput, project)
                }
                null
            }

            val subscribeMethod = connection.javaClass.methods.find {
                it.name == "subscribe" && it.parameterCount == 2
            }
            subscribeMethod?.invoke(connection, topic, proxy)

            LOG.debug("Subscribed to CompilationStatusListener for structured build messages")
        } catch (e: ClassNotFoundException) {
            LOG.debug("CompilerTopics not available (no Java plugin), structured messages will be empty")
        } catch (e: Exception) {
            LOG.warn("Failed to subscribe to CompilationStatusListener", e)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractCompilerMessages(
        compileContext: Any,
        messages: MutableList<BuildMessage>,
        rawOutput: StringBuilder,
        includeRawOutput: Boolean,
        project: Project
    ) {
        try {
            val categoryClass = Class.forName("com.intellij.openapi.compiler.CompilerMessageCategory")
            val getMessagesMethod = compileContext.javaClass.getMethod("getMessages", categoryClass)

            for (categoryName in listOf("ERROR", "WARNING")) {
                val category = java.lang.Enum.valueOf(
                    categoryClass as Class<out Enum<*>>,
                    categoryName
                )
                val compilerMessages = getMessagesMethod.invoke(compileContext, category) as? Array<*> ?: continue

                for (msg in compilerMessages) {
                    if (msg == null) continue
                    val msgClass = msg.javaClass

                    val messageText = msgClass.getMethod("getMessage").invoke(msg) as? String ?: continue
                    val virtualFile = msgClass.getMethod("getVirtualFile").invoke(msg) as? com.intellij.openapi.vfs.VirtualFile
                    val filePath = virtualFile?.let { getRelativePath(project, it) }

                    var line: Int? = null
                    var column: Int? = null
                    try {
                        val navigatable = msgClass.getMethod("getNavigatable").invoke(msg)
                        if (navigatable != null) {
                            val openFileDescriptor = navigatable as? com.intellij.openapi.fileEditor.OpenFileDescriptor
                            if (openFileDescriptor != null) {
                                line = openFileDescriptor.line + 1
                                column = openFileDescriptor.column + 1
                            }
                        }
                    } catch (_: Exception) { }

                    messages.add(BuildMessage(
                        category = categoryName,
                        message = messageText,
                        file = filePath,
                        line = line,
                        column = column
                    ))
                }
            }

            if (includeRawOutput) {
                for (categoryName in listOf("INFORMATION", "STATISTICS")) {
                    try {
                        val category = java.lang.Enum.valueOf(
                            categoryClass as Class<out Enum<*>>,
                            categoryName
                        )
                        val infoMessages = getMessagesMethod.invoke(compileContext, category) as? Array<*> ?: continue
                        for (msg in infoMessages) {
                            if (msg == null) continue
                            val text = msg.javaClass.getMethod("getMessage").invoke(msg) as? String ?: continue
                            rawOutput.appendLine(text)
                        }
                    } catch (_: Exception) { }
                }
            }
        } catch (e: Exception) {
            LOG.warn("Failed to extract compiler messages", e)
        }
    }
}
