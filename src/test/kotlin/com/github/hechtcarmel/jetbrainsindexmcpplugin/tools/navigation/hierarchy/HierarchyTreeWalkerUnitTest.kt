package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.navigation.hierarchy

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.progress.ProcessCanceledException
import junit.framework.TestCase
import java.lang.reflect.InvocationTargetException

class HierarchyTreeWalkerUnitTest : TestCase() {

    fun testHierarchyKindIsCallTrueForCallers() {
        assertTrue(HierarchyKind.CALLERS.isCall)
        assertFalse(HierarchyKind.CALLERS.isType)
    }

    fun testHierarchyKindIsCallTrueForCallees() {
        assertTrue(HierarchyKind.CALLEES.isCall)
        assertFalse(HierarchyKind.CALLEES.isType)
    }

    fun testHierarchyKindIsTypeTrueForSupertypes() {
        assertTrue(HierarchyKind.SUPERTYPES.isType)
        assertFalse(HierarchyKind.SUPERTYPES.isCall)
    }

    fun testHierarchyKindIsTypeTrueForSubtypes() {
        assertTrue(HierarchyKind.SUBTYPES.isType)
        assertFalse(HierarchyKind.SUBTYPES.isCall)
    }

    // --- rethrowIfCancellation (#141): never swallow a read-action cancellation -------------
    // The platform's coroutine readAction{} retries the block when a CannotReadException
    // (a ProcessCanceledException) escapes it; the walker must let that signal through.

    /** Runs the helper and returns whatever it rethrew, or null if it was a no-op. */
    private fun caught(t: Throwable): Throwable? = try {
        rethrowIfCancellation(t)
        null
    } catch (e: Throwable) {
        e
    }

    /** Marker-only ControlFlowException that is NOT a CancellationException. */
    private class FakeControlFlow : RuntimeException(), ControlFlowException

    fun testRethrowsCannotReadExceptionDirectly() {
        val pce = ReadAction.CannotReadException()
        assertSame(pce, caught(pce))
    }

    fun testUnwrapsAndRethrowsCancellationFromInvocationTargetException() {
        // Reflection (Method.invoke) wraps the cancellation; the helper must unwrap and
        // rethrow the ORIGINAL CannotReadException, not the InvocationTargetException.
        val pce = ReadAction.CannotReadException()
        assertSame(pce, caught(InvocationTargetException(pce)))
    }

    fun testRethrowsProcessCanceledException() {
        val pce = ProcessCanceledException()
        assertSame(pce, caught(pce))
    }

    fun testRethrowsControlFlowException() {
        val cf = FakeControlFlow()
        assertSame(cf, caught(cf))
    }

    fun testIgnoresOrdinaryThrowable() {
        // Genuine failures keep flowing through the caller's normal failure handling.
        assertNull(caught(IllegalStateException("genuine failure")))
    }

    fun testIgnoresOrdinaryThrowableWrappedInInvocationTargetException() {
        assertNull(caught(InvocationTargetException(IllegalStateException("genuine failure"))))
    }
}
