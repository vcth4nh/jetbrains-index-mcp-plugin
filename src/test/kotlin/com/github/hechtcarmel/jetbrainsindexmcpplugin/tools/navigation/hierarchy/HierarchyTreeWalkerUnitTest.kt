package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.navigation.hierarchy

import junit.framework.TestCase

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
}
