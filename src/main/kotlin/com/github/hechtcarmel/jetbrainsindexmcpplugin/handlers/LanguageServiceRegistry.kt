package com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import java.util.concurrent.ConcurrentHashMap

object LanguageServiceRegistry {

    private val LOG = logger<LanguageServiceRegistry>()

    private val services = ConcurrentHashMap<String, LanguageService>()
    private var initialized = false

    private val serviceClassNames = listOf(
        // JavaLanguageService migrated to EP (languageKindResolver + superMethodsProvider)
        // KotlinLanguageService migrated to EP (languageKindResolver + superMethodsProvider)
        "com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.python.PythonLanguageService",
        // JavaScriptLanguageService migrated to EP (superMethodsProvider)
        "com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.go.GoLanguageService",
        "com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.php.PhpLanguageService",
        "com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.rust.RustLanguageService",
    )

    @Synchronized
    fun registerServices() {
        if (initialized) return
        initialized = true

        LOG.info("Registering language services...")

        for (className in serviceClassNames) {
            try {
                val serviceClass = Class.forName(className)
                val service = serviceClass.getDeclaredConstructor().newInstance() as LanguageService
                if (!service.isAvailable()) {
                    LOG.info("${service.displayName} language service not available (plugin not installed)")
                    continue
                }
                val ids = service.languageIds
                if (ids.isEmpty()) {
                    LOG.warn("${service.displayName} language service returned empty languageIds, skipping")
                    continue
                }
                for (id in ids) {
                    services[id] = service
                }
                LOG.info("Registered ${service.displayName} language service for IDs: $ids")
            } catch (e: ClassNotFoundException) {
                LOG.info("Language service $className not found")
            } catch (e: Exception) {
                LOG.warn("Failed to register language service $className: ${e.message}", e)
            }
        }

        LOG.info("Language services registered: ${services.size} language IDs mapped")
    }

    @Synchronized
    fun clear() {
        services.clear()
        initialized = false
    }

    fun getService(element: PsiElement): LanguageService? {
        val language = element.language
        services[language.id]?.let { return it }
        language.baseLanguage?.let { base -> services[base.id]?.let { return it } }
        return null
    }

    fun getKind(element: PsiElement): String {
        val service = getService(element)
        if (service != null) return service.getKind(element)
        val ideType = com.intellij.lang.findUsages.LanguageFindUsages.getType(element)
        if (ideType.isNotEmpty()) return LanguageService.normalizeKind(ideType)
        return LanguageService.fallbackKindFromClassName(element.javaClass.simpleName)
    }

    fun findSuperMethods(element: PsiElement, project: Project): SuperMethodsData? {
        return getService(element)?.findSuperMethods(element, project)
    }

    fun hasSuperMethodsSupport(): Boolean {
        return services.values.any { it.supportsSuperMethods }
    }
}
