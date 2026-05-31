package com.github.hechtcarmel.jetbrainsindexmcpplugin.exceptions

import com.github.hechtcarmel.jetbrainsindexmcpplugin.server.models.JsonRpcErrorCodes
import junit.framework.TestCase

class McpExceptionUnitTest : TestCase() {

    fun testErrorTypeMappings() {
        assertEquals("index_not_ready", IndexNotReadyException("x").errorType)
        assertEquals("file_not_found", FileNotFoundException("/p").errorType)
        assertEquals("symbol_not_found", SymbolNotFoundException("x").errorType)
        assertEquals("refactoring_conflict", RefactoringConflictException("x").errorType)
        assertEquals("invalid_params", InvalidParamsException("x").errorType)
        assertEquals("internal_error", InternalErrorException("x").errorType)
        assertEquals("parse_error", ParseErrorException("x").errorType)
        assertEquals("invalid_request", InvalidRequestException("x").errorType)
        assertEquals("method_not_found", MethodNotFoundException("m").errorType)
    }

    fun testErrorTypeCoexistsWithErrorCode() {
        val e: McpException = IndexNotReadyException("x")
        assertEquals("index_not_ready", e.errorType)
        assertEquals(JsonRpcErrorCodes.INDEX_NOT_READY, e.errorCode)
    }
}
