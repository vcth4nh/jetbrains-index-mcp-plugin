package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.intelligence

import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.ProblemInfo
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.ProblemHighlightFilter
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightingSessionImpl
import com.intellij.codeInsight.multiverse.anyContext
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ProperTextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.annotations.TestOnly

@Service(Service.Level.PROJECT)
class DiagnosticsAnalysisService(private val project: Project) {

    companion object {
        private val LOG = logger<DiagnosticsAnalysisService>()
        private const val DEFAULT_ANALYSIS_TIMEOUT_MS = 15_000L
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 25L

        fun getInstance(project: Project): DiagnosticsAnalysisService =
            project.getService(DiagnosticsAnalysisService::class.java)
    }

    private val analysisMutex = Mutex()

    @TestOnly
    internal var analysisTimeoutMsOverride: Long? = null

    @TestOnly
    internal var mainPassesRunnerOverride: (suspend (MainPassesRequest) -> List<HighlightInfo>)? = null

    suspend fun analyzeFile(
        virtualFile: VirtualFile,
        filePath: String,
        severity: String,
        startLine: Int?,
        endLine: Int?,
        maxProblems: Int
    ): FileAnalysisResult {
        val fileContext = ReadAction.compute<FileContext?, Throwable> {
            if (!virtualFile.isValid) {
                return@compute null
            }

            val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return@compute null
            if (!ProblemHighlightFilter.shouldProcessFileInBatch(psiFile)) {
                return@compute null
            }

            val document = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return@compute null
            FileContext(psiFile = psiFile, filePath = filePath, document = document)
        }

        if (fileContext == null) {
            return FileAnalysisResult(
                problems = emptyList(),
                highlights = emptyList(),
                analysisFresh = false,
                analysisTimedOut = false,
                analysisMessage = "File is not eligible for batch diagnostics analysis."
            )
        }

        return analysisMutex.withLock {
            val timeoutMs = analysisTimeoutMsOverride ?: DEFAULT_ANALYSIS_TIMEOUT_MS
            val highlights = withTimeoutOrNull(timeoutMs) {
                runMainPassesWithRetries(fileContext)
            }

            if (highlights == null) {
                return@withLock FileAnalysisResult(
                    problems = emptyList(),
                    highlights = emptyList(),
                    analysisFresh = false,
                    analysisTimedOut = true,
                    analysisMessage = "File diagnostics analysis timed out after ${timeoutMs}ms."
                )
            }

            FileAnalysisResult(
                problems = toProblemInfoList(
                    highlights = highlights,
                    filePath = filePath,
                    document = fileContext.document,
                    severity = severity,
                    startLine = startLine,
                    endLine = endLine,
                    maxProblems = maxProblems
                ),
                highlights = highlights,
                analysisFresh = true,
                analysisTimedOut = false,
                analysisMessage = null
            )
        }
    }

    private suspend fun runMainPassesWithRetries(fileContext: FileContext): List<HighlightInfo> {
        var lastProcessCanceled: ProcessCanceledException? = null

        repeat(MAX_RETRIES) { attemptIndex ->
            try {
                return runSingleMainPassAttempt(fileContext, attemptIndex + 1)
            } catch (e: ProcessCanceledException) {
                if (!isRetriable(e)) {
                    throw e
                }

                lastProcessCanceled = e
                awaitWriteActionCompletion()
            }
        }

        throw lastProcessCanceled ?: ProcessCanceledException()
    }

    private suspend fun runSingleMainPassAttempt(
        fileContext: FileContext,
        attempt: Int
    ): List<HighlightInfo> {
        val overrideRunner = mainPassesRunnerOverride
        if (overrideRunner != null) {
            return overrideRunner(
                MainPassesRequest(
                    filePath = fileContext.filePath,
                    psiFile = fileContext.psiFile,
                    document = fileContext.document,
                    attempt = attempt
                )
            )
        }

        return withContext(Dispatchers.Default) {
            val codeAnalyzer = DaemonCodeAnalyzer.getInstance(project) as? DaemonCodeAnalyzerImpl
                ?: return@withContext emptyList()

            val daemonIndicator = DaemonProgressIndicator()
            val cancellationHandle = currentCoroutineContext().job.invokeOnCompletion { cause ->
                if (cause != null) {
                    daemonIndicator.cancel("Diagnostics analysis coroutine cancelled")
                }
            }

            try {
                var collectedHighlights = emptyList<HighlightInfo>()
                ProgressManager.getInstance().executeProcessUnderProgress({
                    val range = ProperTextRange.create(0, fileContext.document.textLength)
                    HighlightingSessionImpl.runInsideHighlightingSession(
                        fileContext.psiFile,
                        anyContext(),
                        null,
                        range,
                        false
                    ) {
                        collectedHighlights = codeAnalyzer.runMainPasses(
                            fileContext.psiFile,
                            fileContext.document,
                            daemonIndicator
                        )
                    }
                }, daemonIndicator)
                collectedHighlights
            } finally {
                cancellationHandle.dispose()
            }
        }
    }

    private suspend fun awaitWriteActionCompletion() {
        repeat(40) {
            if (!ApplicationManagerEx.getApplicationEx().isWriteActionPending) {
                return
            }
            delay(RETRY_DELAY_MS)
        }
    }

    private fun isRetriable(exception: ProcessCanceledException): Boolean {
        val cause = exception.cause
        return cause == null || cause.javaClass == Throwable::class.java
    }

    private fun toProblemInfoList(
        highlights: List<HighlightInfo>,
        filePath: String,
        document: com.intellij.openapi.editor.Document,
        severity: String,
        startLine: Int?,
        endLine: Int?,
        maxProblems: Int
    ): List<ProblemInfo> {
        val problems = mutableListOf<ProblemInfo>()
        val seen = linkedSetOf<String>()

        for (highlight in highlights) {
            if (highlight.severity.myVal < HighlightSeverity.WEAK_WARNING.myVal) {
                continue
            }

            val matchesSeverity = when (severity) {
                "errors" -> highlight.severity.myVal >= HighlightSeverity.ERROR.myVal
                "warnings" -> highlight.severity.myVal < HighlightSeverity.ERROR.myVal
                else -> true
            }

            if (!matchesSeverity) {
                continue
            }

            val problem = highlight.toProblemInfo(document, filePath)
            val inRange = (startLine == null || problem.line >= startLine) &&
                (endLine == null || problem.line <= endLine)

            if (!inRange) {
                continue
            }

            val key = "${problem.line}:${problem.column}:${problem.message}"
            if (!seen.add(key)) {
                continue
            }

            problems.add(problem)
            if (problems.size >= maxProblems) {
                break
            }
        }

        return problems
    }

    private fun HighlightInfo.toProblemInfo(
        document: com.intellij.openapi.editor.Document,
        filePath: String
    ): ProblemInfo {
        val safeStartOffset = startOffset.coerceIn(0, document.textLength)
        val safeEndOffset = endOffset.coerceIn(safeStartOffset, document.textLength)

        val problemLine = document.getLineNumber(safeStartOffset) + 1
        val problemColumn = safeStartOffset - document.getLineStartOffset(problemLine - 1) + 1
        val endLineNum = document.getLineNumber(safeEndOffset) + 1
        val endColumnNum = safeEndOffset - document.getLineStartOffset(endLineNum - 1) + 1

        val severityString = when {
            severity.myVal >= HighlightSeverity.ERROR.myVal -> "ERROR"
            severity.myVal >= HighlightSeverity.WARNING.myVal -> "WARNING"
            severity.myVal >= HighlightSeverity.WEAK_WARNING.myVal -> "WEAK_WARNING"
            else -> "INFO"
        }

        return ProblemInfo(
            message = description ?: "Unknown problem",
            severity = severityString,
            file = filePath,
            line = problemLine,
            column = problemColumn,
            endLine = endLineNum,
            endColumn = endColumnNum
        )
    }

    internal data class MainPassesRequest(
        val filePath: String,
        val psiFile: PsiFile,
        val document: com.intellij.openapi.editor.Document,
        val attempt: Int
    )

    data class FileAnalysisResult(
        val problems: List<ProblemInfo>,
        val highlights: List<HighlightInfo>,
        val analysisFresh: Boolean,
        val analysisTimedOut: Boolean,
        val analysisMessage: String?
    )

    private data class FileContext(
        val psiFile: PsiFile,
        val filePath: String,
        val document: com.intellij.openapi.editor.Document
    )
}
