package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.navigation.hierarchy

enum class HierarchyKind {
    CALLERS,
    CALLEES,
    SUPERTYPES,
    SUBTYPES;

    val isCall: Boolean get() = this == CALLERS || this == CALLEES
    val isType: Boolean get() = this == SUPERTYPES || this == SUBTYPES
}
