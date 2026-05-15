package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.navigation.hierarchy

import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.BuiltInSearchScope
import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.TypeElementData
import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.TypeHierarchyData
import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.createNavigationSearchScope
import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.shouldIncludeNavigationElement
import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.rust.BaseRustHandler
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.PluginDetectors
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.QualifiedNameUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.DefinitionsScopedSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor

/**
 * Relocated algorithm from the former [com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.rust.RustTypeHierarchyHandler].
 *
 * Handles Rust-specific type relationships:
 * - **For Traits**: Shows supertraits and implementing types
 * - **For Structs/Enums**: Shows implemented traits (via impl blocks)
 * - **For Impl Blocks**: Shows the trait/type relationship
 *
 * Note: Rust does NOT have class inheritance. Composition and trait
 * implementations are the primary mechanisms for code reuse.
 */
internal class RustTypeHierarchyImpl : BaseRustHandler<TypeHierarchyData>() {

    companion object {
        private const val MAX_HIERARCHY_DEPTH = 50
    }

    private val rsNamedElementIndexClass: Class<*>? by lazy {
        try { Class.forName("org.rust.lang.core.psi.ext.RsNamedElementIndex") }
        catch (_: ClassNotFoundException) { null }
    }

    override val languageId = "Rust"

    override fun canHandle(element: PsiElement): Boolean {
        return isAvailable() && isRustLanguage(element)
    }

    override fun isAvailable(): Boolean = PluginDetectors.rust.isAvailable && rsTraitItemClass != null

    fun getTypeHierarchy(
        element: PsiElement,
        project: Project,
        scope: BuiltInSearchScope
    ): TypeHierarchyData? {
        LOG.debug("Getting type hierarchy for Rust element at ${element.containingFile?.name}")
        val searchScope = createNavigationSearchScope(project, scope)

        // Handle traits
        val trait = findContainingRsTrait(element)
        if (trait != null) {
            LOG.debug("Getting hierarchy for trait: ${getName(trait)}")
            return getTraitHierarchy(project, trait, searchScope)
        }

        // Handle structs
        val struct = findContainingRsStruct(element)
        if (struct != null) {
            LOG.debug("Getting hierarchy for struct: ${getName(struct)}")
            return getTypeImplHierarchy(project, struct, searchScope)
        }

        // Handle enums
        val enum = findContainingRsEnum(element)
        if (enum != null) {
            LOG.debug("Getting hierarchy for enum: ${getName(enum)}")
            return getTypeImplHierarchy(project, enum, searchScope)
        }

        // Handle impl blocks
        val impl = findContainingRsImpl(element)
        if (impl != null) {
            LOG.debug("Getting hierarchy for impl block")
            return getImplHierarchy(project, impl, searchScope)
        }

        return null
    }

    private fun getTraitHierarchy(
        project: Project,
        trait: PsiElement,
        searchScope: GlobalSearchScope
    ): TypeHierarchyData {
        val supertypes = getSupertraitHierarchy(project, trait, mutableSetOf(), searchScope = searchScope)
        val subtypes = getImplementingTypes(project, trait, searchScope)

        LOG.debug("Found ${supertypes.size} supertraits and ${subtypes.size} implementing types")

        return TypeHierarchyData(
            element = TypeElementData(
                name = getName(trait) ?: "unknown",
                qualifiedName = QualifiedNameUtil.getQualifiedName(trait),
                file = trait.containingFile?.virtualFile?.let { getRelativePath(project, it) },
                line = getLineNumber(project, trait),
                kind = "TRAIT",
                language = "Rust",
                psi = trait
            ),
            supertypes = supertypes,
            subtypes = subtypes
        )
    }

    private fun getSupertraitHierarchy(
        project: Project,
        trait: PsiElement,
        visited: MutableSet<String>,
        depth: Int = 0,
        searchScope: GlobalSearchScope
    ): List<TypeElementData> {
        if (depth > MAX_HIERARCHY_DEPTH) return emptyList()

        val traitName = getName(trait) ?: return emptyList()
        if (traitName in visited) return emptyList()
        visited.add(traitName)

        val supertypes = mutableListOf<TypeElementData>()

        try {
            val superTraits = getSuperTraits(trait) ?: emptyList()
            for (superTraitRef in superTraits) {
                val resolved = resolveReference(superTraitRef)
                if (
                    resolved != null &&
                    isRsTrait(resolved) &&
                    shouldIncludeNavigationElement(searchScope, resolved)
                ) {
                    val resolvedName = getName(resolved) ?: continue
                    if (resolvedName !in visited) {
                        val nestedSupertypes = getSupertraitHierarchy(project, resolved, visited, depth + 1, searchScope)
                        supertypes.add(TypeElementData(
                            name = resolvedName,
                            qualifiedName = QualifiedNameUtil.getQualifiedName(resolved),
                            file = resolved.containingFile?.virtualFile?.let { getRelativePath(project, it) },
                            line = getLineNumber(project, resolved),
                            kind = "TRAIT",
                            language = "Rust",
                            supertypes = nestedSupertypes.takeIf { it.isNotEmpty() },
                            psi = resolved
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            LOG.debug("Error getting supertraits: ${e.message}")
        }

        return supertypes
    }

    /**
     * Looks up a Rust struct by name via RsNamedElementIndex.
     *
     * Used as a fallback when reference resolution fails on an impl's type reference,
     * to avoid tagging clearly-named types as kind: IMPL. The Rust plugin's index
     * API surface is not always stable across versions; if reflection fails we
     * gracefully return null (preserving the original IMPL fallthrough behavior).
     */
    private fun lookupStructByName(project: Project, name: String): PsiElement? {
        val indexClass = rsNamedElementIndexClass ?: return null
        return try {
            val getInstanceMethod = indexClass.getMethod("getInstance")
            val instance = getInstanceMethod.invoke(null)
            // RsNamedElementIndex.findElementsByName(project, name) returns Collection<RsNamedElement>
            val findMethod = instance.javaClass.getMethod("findElementsByName", Project::class.java, String::class.java)
            val results = findMethod.invoke(instance, project, name) as? Collection<*>
            results?.filterIsInstance<PsiElement>()?.firstOrNull { isRsStruct(it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun getImplementingTypes(
        project: Project,
        trait: PsiElement,
        searchScope: GlobalSearchScope
    ): List<TypeElementData> {
        val results = mutableListOf<TypeElementData>()

        try {
            DefinitionsScopedSearch.search(trait, searchScope).forEach(Processor { definition ->
                if (isRsImpl(definition) && shouldIncludeNavigationElement(searchScope, definition)) {
                    val typeRef = getTypeReference(definition)
                    if (typeRef != null) {
                        var resolvedType = resolveReference(typeRef)
                        val typeName = resolvedType?.let { getName(it) } ?: typeRef.text?.trim()

                        // Fallback: if reference resolution failed but we have a name, look up the struct.
                        if (resolvedType == null && typeName != null) {
                            resolvedType = lookupStructByName(project, typeName)
                        }

                        // Skip the entry when both reference resolution and name lookup
                        // fail. Falling back to `psi = definition` (the impl block) breaks
                        // wire format: RsImplItem is not a PsiNamedElement, so downstream
                        // conversion grabs psi.text and surfaces raw `impl Foo for Bar { … }`
                        // body source as the entry's name.
                        if (typeName != null && resolvedType != null) {
                            results.add(TypeElementData(
                                name = typeName,
                                qualifiedName = QualifiedNameUtil.getQualifiedName(resolvedType),
                                file = resolvedType.containingFile?.virtualFile?.let { getRelativePath(project, it) },
                                line = getLineNumber(project, resolvedType),
                                kind = determineElementKind(resolvedType),
                                language = "Rust",
                                psi = resolvedType
                            ))
                        }
                    }
                }
                results.size < 100
            })

            LOG.debug("Found ${results.size} implementing types via DefinitionsScopedSearch")
        } catch (e: Exception) {
            LOG.debug("Error getting implementing types: ${e.message}")
        }

        return results
    }

    private fun getTypeImplHierarchy(
        project: Project,
        type: PsiElement,
        searchScope: GlobalSearchScope
    ): TypeHierarchyData {
        val implementedTraits = findImplementedTraits(project, type, searchScope)

        return TypeHierarchyData(
            element = TypeElementData(
                name = getName(type) ?: "unknown",
                qualifiedName = QualifiedNameUtil.getQualifiedName(type),
                file = type.containingFile?.virtualFile?.let { getRelativePath(project, it) },
                line = getLineNumber(project, type),
                kind = determineElementKind(type),
                language = "Rust",
                psi = type
            ),
            supertypes = implementedTraits,  // Implemented traits shown as "supertypes"
            subtypes = emptyList()           // Rust has no type inheritance
        )
    }

    private fun findImplementedTraits(
        project: Project,
        type: PsiElement,
        searchScope: GlobalSearchScope
    ): List<TypeElementData> {
        val results = mutableListOf<TypeElementData>()

        try {
            // Search for references to this type to find impl blocks
            ReferencesSearch.search(type, searchScope).forEach(Processor { reference ->
                val impl = findContainingRsImpl(reference.element)
                if (impl != null && shouldIncludeNavigationElement(searchScope, impl)) {
                    val traitRef = getTraitRef(impl)
                    if (traitRef != null) {
                        val resolvedTrait = resolveReference(traitRef)
                        // Skip if resolution failed — we cannot point the wire-format
                        // entry at the unnamed RsImplItem block (its text would surface
                        // as the raw `impl Foo for Bar { … }` source).
                        if (resolvedTrait != null) {
                            val traitName = getName(resolvedTrait)
                            if (traitName != null && results.none { it.name == traitName }) {
                                results.add(TypeElementData(
                                    name = traitName,
                                    qualifiedName = QualifiedNameUtil.getQualifiedName(resolvedTrait),
                                    file = resolvedTrait.containingFile?.virtualFile?.let { getRelativePath(project, it) },
                                    line = getLineNumber(project, resolvedTrait),
                                    kind = "TRAIT",
                                    language = "Rust",
                                    psi = resolvedTrait
                                ))
                            }
                        }
                    }
                }
                results.size < 100
            })
        } catch (e: Exception) {
            LOG.debug("Error finding implemented traits: ${e.message}")
        }

        return results
    }

    private fun getImplHierarchy(
        project: Project,
        impl: PsiElement,
        searchScope: GlobalSearchScope
    ): TypeHierarchyData {
        val traitRef = getTraitRef(impl)
        val typeRef = getTypeReference(impl)

        val supertypes = mutableListOf<TypeElementData>()
        val subtypes = mutableListOf<TypeElementData>()

        // If implementing a trait, show the trait as a supertype.
        // Skip if resolution failed: pointing at the impl block leaks raw block source.
        if (traitRef != null) {
            val resolvedTrait = resolveReference(traitRef)
            if (resolvedTrait != null && shouldIncludeNavigationElement(searchScope, resolvedTrait)) {
                val traitName = getName(resolvedTrait)
                if (traitName != null) {
                    supertypes.add(TypeElementData(
                        name = traitName,
                        qualifiedName = QualifiedNameUtil.getQualifiedName(resolvedTrait),
                        file = resolvedTrait.containingFile?.virtualFile?.let { getRelativePath(project, it) },
                        line = getLineNumber(project, resolvedTrait),
                        kind = "TRAIT",
                        language = "Rust",
                        psi = resolvedTrait
                    ))
                }
            }
        }

        // Show the implementing type as a subtype.
        // Skip if resolution failed (same reason as above).
        if (typeRef != null) {
            val resolvedType = resolveReference(typeRef)
            if (resolvedType != null && shouldIncludeNavigationElement(searchScope, resolvedType)) {
                val typeName = getName(resolvedType)
                if (typeName != null) {
                    subtypes.add(TypeElementData(
                        name = typeName,
                        qualifiedName = QualifiedNameUtil.getQualifiedName(resolvedType),
                        file = resolvedType.containingFile?.virtualFile?.let { getRelativePath(project, it) },
                        line = getLineNumber(project, resolvedType),
                        kind = determineElementKind(resolvedType),
                        language = "Rust",
                        psi = resolvedType
                    ))
                }
            }
        }

        val implName = buildImplName(traitRef, typeRef)

        return TypeHierarchyData(
            element = TypeElementData(
                name = implName,
                qualifiedName = null,
                file = impl.containingFile?.virtualFile?.let { getRelativePath(project, it) },
                line = getLineNumber(project, impl),
                kind = "IMPL",
                language = "Rust",
                psi = impl
            ),
            supertypes = supertypes,
            subtypes = subtypes
        )
    }

    private fun buildImplName(traitRef: PsiElement?, typeRef: PsiElement?): String {
        val traitName = traitRef?.text?.trim()
        val typeName = typeRef?.text?.trim()

        return when {
            traitName != null && typeName != null -> "impl $traitName for $typeName"
            typeName != null -> "impl $typeName"
            else -> "impl"
        }
    }
}
