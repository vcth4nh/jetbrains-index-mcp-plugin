package com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers

import com.intellij.lang.Language
import com.intellij.openapi.diagnostic.logger
import com.intellij.psi.PsiElement
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry for language-specific handlers.
 *
 * The registry manages the lifecycle of language handlers and provides methods
 * to find the appropriate handler for a given PSI element or language.
 *
 * ## Handler Registration
 *
 * Handlers are registered at startup based on plugin availability:
 *
 * ```kotlin
 * LanguageHandlerRegistry.registerHandlers()
 * ```
 *
 * ## Finding Handlers
 *
 * To find a handler for a specific element:
 *
 * ```kotlin
 * val handler = LanguageHandlerRegistry.getHandler<TypeHierarchyHandler>(element)
 * ```
 *
 * @see LanguageHandler
 */
object LanguageHandlerRegistry {

    private val LOG = logger<LanguageHandlerRegistry>()

    // Handler registries by type
    private val implementationsHandlers = ConcurrentHashMap<String, ImplementationsHandler>()
    private val symbolReferenceHandlers = ConcurrentHashMap<String, SymbolReferenceHandler>()
    private val superMethodsHandlers = ConcurrentHashMap<String, SuperMethodsHandler>()

    // Track if handlers have been registered
    private var initialized = false

    /**
     * Registers all available language handlers.
     *
     * This method discovers and registers handlers based on available plugins.
     * It's called during plugin startup.
     */
    @Synchronized
    fun registerHandlers() {
        if (initialized) return
        initialized = true

        LOG.info("Registering language handlers...")

        for (reg in handlerRegistrations) {
            registerLanguageHandlers(reg.className, reg.displayName)
        }

        LOG.info("Language handlers registered: " +
            "Implementations=${implementationsHandlers.size}, " +
            "SymbolReference=${symbolReferenceHandlers.size}, " +
            "SuperMethods=${superMethodsHandlers.size}")
    }

    /**
     * Clears all registered handlers. Used for testing.
     */
    @Synchronized
    fun clear() {
        implementationsHandlers.clear()
        symbolReferenceHandlers.clear()
        superMethodsHandlers.clear()
        initialized = false
    }

    // Registration methods

    fun registerImplementationsHandler(handler: ImplementationsHandler) {
        implementationsHandlers[handler.languageId] = handler
        LOG.info("Registered ImplementationsHandler for ${handler.languageId}")
    }

    fun registerSymbolReferenceHandler(handler: SymbolReferenceHandler) {
        symbolReferenceHandlers[handler.languageId] = handler
        LOG.info("Registered SymbolReferenceHandler for ${handler.languageId}")
    }

    fun registerSuperMethodsHandler(handler: SuperMethodsHandler) {
        superMethodsHandlers[handler.languageId] = handler
        LOG.info("Registered SuperMethodsHandler for ${handler.languageId}")
    }

    // Handler lookup methods

    /**
     * Gets an implementations handler for the given element.
     */
    fun getImplementationsHandler(element: PsiElement): ImplementationsHandler? {
        return findHandler(element, implementationsHandlers)
    }

    /**
     * Gets a super methods handler for the given element.
     */
    fun getSuperMethodsHandler(element: PsiElement): SuperMethodsHandler? {
        return findHandler(element, superMethodsHandlers)
    }

    /**
     * Checks if any handlers are available for the given handler type.
     */
    fun hasImplementationsHandlers(): Boolean = implementationsHandlers.values.any { it.isAvailable() }
    fun hasSuperMethodsHandlers(): Boolean = superMethodsHandlers.values.any { it.isAvailable() }

    /**
     * Gets a list of languages that have handlers for the given handler type.
     */
    fun getSupportedLanguagesForImplementations(): List<String> =
        implementationsHandlers.filter { it.value.isAvailable() }.keys.toList()

    fun getSupportedLanguagesForSuperMethods(): List<String> =
        superMethodsHandlers.filter { it.value.isAvailable() }.keys.toList()

    fun getSupportedLanguagesForSymbolReference(): List<String> =
        symbolReferenceHandlers.filter { it.value.isAvailable() }.keys.toList()

    /**
     * Gets a list of human-readable language names that have symbol reference handlers available.
     *
     * @return A list of language names (e.g., "Java", "Python") that have available symbol reference handlers.
     */
    fun getSupportedLanguageNamesForSymbolReference(): List<String> {
        return symbolReferenceHandlers.filter { it.value.isAvailable() }.map { it.value.languageName }
    }

    /**
     * Gets a symbol reference handler for the given language name.
     *
     * @param languageName The language name (e.g., "Java", "Kotlin")
     * @return The handler if available, or null if no handler exists for the language
     */
    fun getSymbolReferenceHandlerByLanguageName(languageName: String): SymbolReferenceHandler? {
        return symbolReferenceHandlers.values.firstOrNull {
            it.isAvailable() && it.languageName.equals(languageName, ignoreCase = true)
        }
    }

    // Private helper methods

    private fun <T : LanguageHandler<*>> findHandler(
        element: PsiElement,
        handlers: Map<String, T>
    ): T? {
        val language = detectLanguage(element)

        // Try exact language match first
        handlers[language.id]?.let { handler ->
            if (handler.isAvailable() && handler.canHandle(element)) {
                return handler
            }
        }

        // Try base language (e.g., TypeScript -> JavaScript)
        language.baseLanguage?.let { baseLanguage ->
            handlers[baseLanguage.id]?.let { handler ->
                if (handler.isAvailable() && handler.canHandle(element)) {
                    return handler
                }
            }
        }

        // Try all handlers that can handle this element
        return handlers.values.firstOrNull { handler ->
            handler.isAvailable() && handler.canHandle(element)
        }
    }

    /**
     * Detects the language of a PSI element.
     */
    private fun detectLanguage(element: PsiElement): Language {
        return element.language
    }

    private data class HandlerRegistration(val className: String, val displayName: String)

    private val handlerRegistrations = listOf(
        HandlerRegistration("com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.java.JavaHandlers", "Java"),
        HandlerRegistration("com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.python.PythonHandlers", "Python"),
        HandlerRegistration("com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.javascript.JavaScriptHandlers", "JavaScript"),
        HandlerRegistration("com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.go.GoHandlers", "Go"),
        HandlerRegistration("com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.php.PhpHandlers", "PHP"),
        HandlerRegistration("com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.rust.RustHandlers", "Rust"),
    )

    private fun registerLanguageHandlers(className: String, displayName: String) {
        try {
            val handlerClass = Class.forName(className)
            val registerMethod = handlerClass.getMethod("register", LanguageHandlerRegistry::class.java)
            registerMethod.invoke(null, this)
            LOG.info("$displayName handlers registered")
        } catch (e: ClassNotFoundException) {
            LOG.info("$displayName handlers not available ($displayName plugin not installed)")
        } catch (e: Exception) {
            LOG.warn("Failed to register $displayName handlers: ${e.message}", e)
        }
    }
}
