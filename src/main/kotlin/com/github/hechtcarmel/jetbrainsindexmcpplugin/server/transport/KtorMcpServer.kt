package com.github.hechtcarmel.jetbrainsindexmcpplugin.server.transport

import com.github.hechtcarmel.jetbrainsindexmcpplugin.McpConstants
import com.github.hechtcarmel.jetbrainsindexmcpplugin.server.JsonRpcHandler
import com.github.hechtcarmel.jetbrainsindexmcpplugin.server.models.JsonRpcError
import com.github.hechtcarmel.jetbrainsindexmcpplugin.server.models.JsonRpcResponse
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.logger
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.delete
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.BindException

/**
 * Embedded Ktor CIO server for MCP protocol.
 *
 * Provides multiple transport modes for MCP clients with configurable port.
 *
 * Supports three transport modes:
 * 1. Streamable HTTP Transport (2025-03-26 spec) — PRIMARY:
 *    - POST /index-mcp/streamable-http → JSON-RPC with Mcp-Session-Id header
 *    - GET  /index-mcp/streamable-http → 405 Method Not Allowed
 *    - DELETE /index-mcp/streamable-http → Session termination
 *
 * 2. Legacy SSE Transport (2024-11-05 spec):
 *    - GET /index-mcp/sse → Opens SSE stream, sends `endpoint` event with POST URL
 *    - POST /index-mcp?sessionId=xxx → JSON-RPC messages, response sent via SSE
 *
 * 3. Stateless HTTP (convenience):
 *    - POST /index-mcp (no sessionId) → JSON-RPC messages, immediate JSON response
 */
class KtorMcpServer(
    private val port: Int,
    private val host: String = McpConstants.DEFAULT_SERVER_HOST,
    private val jsonRpcHandler: JsonRpcHandler,
    private val sseSessionManager: KtorSseSessionManager,
    private val streamableHttpSessionManager: StreamableHttpSessionManager,
    private val coroutineScope: CoroutineScope
) : Disposable {

    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    companion object {
        private val LOG = logger<KtorMcpServer>()
    }

    /**
     * Result of attempting to start the server.
     */
    sealed class StartResult {
        data object Success : StartResult()
        data class PortInUse(val port: Int) : StartResult()
        data class Error(val message: String, val cause: Throwable? = null) : StartResult()
    }

    /**
     * Starts the server.
     *
     * @return StartResult indicating success or failure with details
     */
    fun start(): StartResult {
        return try {
            server = embeddedServer(CIO, port = port, host = host) {
                configureCors()
                configureRouting()
            }
            server?.start(wait = false)

            LOG.info("MCP Server started on http://$host:$port")
            StartResult.Success
        } catch (e: BindException) {
            LOG.warn("Port $port is already in use", e)
            StartResult.PortInUse(port)
        } catch (e: Exception) {
            LOG.error("Failed to start MCP server", e)
            StartResult.Error(e.message ?: "Unknown error", e)
        }
    }

    /**
     * Stops the server gracefully.
     */
    fun stop() {
        try {
            server?.stop(1000, 2000)
            server = null
            LOG.info("MCP Server stopped")
        } catch (e: Exception) {
            LOG.warn("Error stopping MCP server", e)
        }
    }

    /**
     * Returns whether the server is currently running.
     */
    fun isRunning(): Boolean = server != null

    override fun dispose() = stop()

    private fun Application.configureCors() {
        install(CORS) {
            anyHost()
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Delete)
            allowMethod(HttpMethod.Options)
            allowHeader(HttpHeaders.ContentType)
            allowHeader(HttpHeaders.Accept)
            allowHeader(McpConstants.MCP_SESSION_ID_HEADER)
            exposeHeader(McpConstants.MCP_SESSION_ID_HEADER)
        }
    }

    private fun Application.configureRouting() {
        routing {
            // === Streamable HTTP Transport (2025-03-26 spec) ===

            post(McpConstants.STREAMABLE_HTTP_ENDPOINT_PATH) {
                handleStreamableHttpPostRequest(call)
            }

            get(McpConstants.STREAMABLE_HTTP_ENDPOINT_PATH) {
                call.respond(HttpStatusCode.MethodNotAllowed)
            }

            delete(McpConstants.STREAMABLE_HTTP_ENDPOINT_PATH) {
                handleStreamableHttpDeleteRequest(call)
            }

            // === Legacy SSE Transport (2024-11-05 spec) ===

            get(McpConstants.SSE_ENDPOINT_PATH) {
                handleSseRequest(call)
            }

            post(McpConstants.MCP_ENDPOINT_PATH) {
                handlePostRequest(call)
            }

            post(McpConstants.SSE_ENDPOINT_PATH) {
                handlePostRequest(call)
            }
        }
    }

    /**
     * Handles GET /index-mcp/sse - Opens SSE stream.
     *
     * Creates a new SSE session and sends the `endpoint` event with the POST URL.
     * Keeps the connection open for sending responses to JSON-RPC requests.
     */
    private suspend fun handleSseRequest(call: ApplicationCall) {
        val sessionId = sseSessionManager.createSession()
        LOG.info("Opening SSE connection, session: $sessionId")

        // Create a channel for sending SSE events
        val eventChannel = Channel<String>(Channel.UNLIMITED)
        sseSessionManager.registerChannel(sessionId, eventChannel)

        try {
            call.response.cacheControl(CacheControl.NoCache(null))
            call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                // Send the endpoint event with POST URL
                val endpointPath = "${McpConstants.MCP_ENDPOINT_PATH}?${McpConstants.SESSION_ID_PARAM}=$sessionId"
                write("event: endpoint\n")
                write("data: $endpointPath\n\n")
                flush()
                LOG.info("SSE session established: $sessionId, endpoint: $endpointPath")

                // Keep connection open and send events from channel
                try {
                    for (event in eventChannel) {
                        write(event)
                        flush()
                    }
                } catch (e: Exception) {
                    LOG.debug("SSE connection closed for session $sessionId: ${e.message}")
                }
            }
        } finally {
            sseSessionManager.removeSession(sessionId)
            LOG.info("SSE connection closed, session: $sessionId")
        }
    }

    /**
     * Handles POST /index-mcp - JSON-RPC requests.
     *
     * Supports two modes:
     * - With sessionId: SSE transport - sends response via SSE, returns 202 Accepted
     * - Without sessionId: Streamable HTTP - returns immediate JSON response
     */
    private suspend fun handlePostRequest(call: ApplicationCall) {
        val body = call.receiveText()
        val sessionId = call.request.queryParameters[McpConstants.SESSION_ID_PARAM]

        if (sessionId.isNullOrBlank()) {
            // Streamable HTTP mode - immediate JSON response
            handleStatelessHttpRequest(call, body)
        } else {
            // SSE transport mode - response via SSE stream
            handleSsePostRequest(call, sessionId, body)
        }
    }

    /**
     * Handles POST /index-mcp/streamable-http — Streamable HTTP transport (MCP 2025-03-26).
     *
     * - initialize request: creates session, returns Mcp-Session-Id header
     * - notifications (no id): validates session, returns 202 Accepted
     * - requests (has id): validates session, returns JSON response
     */
    private suspend fun handleStreamableHttpPostRequest(call: ApplicationCall) {
        val body = call.receiveText()

        if (body.isBlank()) {
            call.respondText(
                createJsonRpcError(null as JsonElement?, -32700, "Empty request body"),
                ContentType.Application.Json,
                HttpStatusCode.BadRequest
            )
            return
        }

        // Parse once to determine message type
        val element = try {
            json.parseToJsonElement(body)
        } catch (e: Exception) {
            call.respondText(
                createJsonRpcError(null as JsonElement?, -32700, "Parse error: ${e.message}"),
                ContentType.Application.Json,
                HttpStatusCode.BadRequest
            )
            return
        }

        if (element is JsonArray) {
            call.respondText(
                createJsonRpcError(null as JsonElement?, -32600, "JSON-RPC batching is not supported"),
                ContentType.Application.Json,
                HttpStatusCode.BadRequest
            )
            return
        }

        val parsed = element.jsonObject

        val method = parsed["method"]?.jsonPrimitive?.contentOrNull
        val hasId = parsed.containsKey("id") && parsed["id"] != JsonNull

        // Initialize: process and create session
        if (method == "initialize") {
            handleStreamableHttpInitialize(call, body)
            return
        }

        // All non-initialize requests require a valid session
        val requestId = parsed["id"]
        val sessionId = call.request.headers[McpConstants.MCP_SESSION_ID_HEADER]
        if (sessionId.isNullOrBlank()) {
            call.respondText(
                createJsonRpcError(requestId, -32600, "Missing ${McpConstants.MCP_SESSION_ID_HEADER} header"),
                ContentType.Application.Json,
                HttpStatusCode.BadRequest
            )
            return
        }

        if (streamableHttpSessionManager.getSession(sessionId) == null) {
            call.respondText(
                createJsonRpcError(requestId, -32600, "Session not found or expired"),
                ContentType.Application.Json,
                HttpStatusCode.NotFound
            )
            return
        }

        // Notifications (no id): fire-and-forget, return 202
        if (!hasId) {
            try {
                withContext(ModalityState.any().asContextElement()) {
                    jsonRpcHandler.handleRequest(body)
                }
            } catch (e: Exception) {
                LOG.debug("Error processing notification: ${e.message}")
            }
            call.respond(HttpStatusCode.Accepted)
            return
        }

        // Regular request: process and return JSON response
        try {
            val response = withContext(ModalityState.any().asContextElement()) {
                jsonRpcHandler.handleRequest(body)
            }
            if (response != null) {
                call.respondText(response, ContentType.Application.Json)
            } else {
                call.respond(HttpStatusCode.Accepted)
            }
        } catch (e: Exception) {
            LOG.error("Error processing MCP request (Streamable HTTP)", e)
            call.respondText(
                createJsonRpcError(null as JsonElement?, -32603, e.message ?: "Internal error"),
                ContentType.Application.Json
            )
        }
    }

    /**
     * Handles initialize request for Streamable HTTP transport.
     * Creates a session and returns Mcp-Session-Id header on the response.
     */
    private suspend fun handleStreamableHttpInitialize(call: ApplicationCall, body: String) {
        try {
            val response = withContext(ModalityState.any().asContextElement()) {
                jsonRpcHandler.handleRequest(body)
            }
            if (response != null) {
                val isSuccess = try {
                    val responseElement = json.parseToJsonElement(response).jsonObject
                    responseElement.containsKey("result")
                } catch (_: Exception) {
                    false
                }
                if (isSuccess) {
                    val sessionId = streamableHttpSessionManager.createSession()
                    call.response.header(McpConstants.MCP_SESSION_ID_HEADER, sessionId)
                }
                call.respondText(response, ContentType.Application.Json)
            } else {
                call.respond(HttpStatusCode.InternalServerError)
            }
        } catch (e: Exception) {
            LOG.error("Error processing initialize (Streamable HTTP)", e)
            call.respondText(
                createJsonRpcError(null as JsonElement?, -32603, e.message ?: "Internal error"),
                ContentType.Application.Json
            )
        }
    }

    /**
     * Handles DELETE /index-mcp/streamable-http — Session termination.
     */
    private suspend fun handleStreamableHttpDeleteRequest(call: ApplicationCall) {
        val sessionId = call.request.headers[McpConstants.MCP_SESSION_ID_HEADER]

        if (sessionId.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, "Missing ${McpConstants.MCP_SESSION_ID_HEADER} header")
            return
        }

        if (streamableHttpSessionManager.getSession(sessionId) == null) {
            call.respond(HttpStatusCode.NotFound, "Session not found")
            return
        }

        streamableHttpSessionManager.removeSession(sessionId)
        call.respond(HttpStatusCode.OK)
    }

    /**
     * Handles POST in Streamable HTTP mode (no sessionId).
     * Returns immediate JSON response.
     */
    private suspend fun handleStatelessHttpRequest(call: ApplicationCall, body: String) {
        if (body.isBlank()) {
            call.respondText(
                createJsonRpcError(null as JsonElement?, -32700, "Empty request body"),
                ContentType.Application.Json
            )
            return
        }

        try {
            val response = withContext(ModalityState.any().asContextElement()) {
                jsonRpcHandler.handleRequest(body)
            }
            if (response != null) {
                call.respondText(response, ContentType.Application.Json)
            } else {
                call.respond(HttpStatusCode.Accepted)
            }
        } catch (e: Exception) {
            LOG.error("Error processing MCP request (Streamable HTTP)", e)
            call.respondText(
                createJsonRpcError(null as JsonElement?, -32603, e.message ?: "Internal error"),
                ContentType.Application.Json
            )
        }
    }

    /**
     * Handles POST in SSE transport mode (with sessionId).
     * Sends response via SSE `message` event, returns 202 Accepted immediately.
     */
    private suspend fun handleSsePostRequest(call: ApplicationCall, sessionId: String, body: String) {
        val session = sseSessionManager.getSession(sessionId)
        if (session == null) {
            LOG.warn("POST request with invalid sessionId: $sessionId")
            call.respond(HttpStatusCode.NotFound, "Session not found: $sessionId")
            return
        }

        if (body.isBlank()) {
            sseSessionManager.sendEvent(
                sessionId,
                "message",
                createJsonRpcError(null as JsonElement?, -32700, "Empty request body")
            )
            call.respond(HttpStatusCode.Accepted)
            return
        }

        // Return 202 Accepted immediately
        call.respond(HttpStatusCode.Accepted)

        // Process request asynchronously and send response via SSE
        coroutineScope.launch {
            try {
                val response = jsonRpcHandler.handleRequest(body)
                if (response != null) {
                    val sent = sseSessionManager.sendEvent(sessionId, "message", response)
                    if (!sent) {
                        LOG.warn("Failed to send response to session $sessionId - session may have closed")
                    }
                }
            } catch (e: Exception) {
                LOG.error("Error processing MCP request (SSE)", e)
                sseSessionManager.sendEvent(
                    sessionId,
                    "message",
                    createJsonRpcError(null as JsonElement?, -32603, e.message ?: "Internal error")
                )
            }
        }
    }

    private val json = Json { encodeDefaults = true; prettyPrint = false }

    private fun createJsonRpcError(id: JsonElement?, code: Int, message: String): String {
        val response = JsonRpcResponse(
            id = id,
            error = JsonRpcError(code = code, message = message)
        )
        return json.encodeToString(response)
    }
}
