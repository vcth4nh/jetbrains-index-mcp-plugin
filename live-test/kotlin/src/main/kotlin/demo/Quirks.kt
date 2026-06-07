package demo

fun quirkLambda(x: String): Int {
    val coerce: (String) -> Int = { it.toInt() }
    return coerce(x)
}

fun quirkFunctionRef(x: String): Int {
    val coerce: (String) -> Int = String::toInt
    return coerce(x)
}

fun quirkApply(x: String): Int {
    return StringBuilder().apply { append(x) }.toString().toInt()
}

fun quirkLet(x: String?): Int {
    return x?.let { it.toInt() } ?: 0
}

fun quirkWith(x: String): Int {
    return with(x) { toInt() }
}

fun quirkRun(x: String): Int = x.run { toInt() }

fun String.coerceTo(default: Int): Int = this.toIntOrNull() ?: default

fun quirkExtensionFn(x: String): Int = x.coerceTo(0)

fun quirkWhen(mode: String, x: String): Int = when (mode) {
    "int" -> x.toInt()
    "abs" -> Math.abs(x.toInt())
    else -> 0
}

sealed class Coercion {
    abstract fun apply(x: String): Int
    object IntCoerce : Coercion() { override fun apply(x: String): Int = x.toInt() }
    object AbsCoerce : Coercion() { override fun apply(x: String): Int = Math.abs(x.toInt()) }
}

fun quirkSealed(c: Coercion, x: String): Int = c.apply(x)

data class Coercer(val prefix: String) {
    fun coerce(x: String): Int = x.removePrefix(prefix).toInt()
}

fun quirkDataClass(x: String): Int = Coercer("+").coerce(x)

fun quirkDispatchMap(key: String, x: String): Int {
    val dispatch: Map<String, (String) -> Int> = mapOf(
        "int" to String::toInt,
        "abs" to { s -> Math.abs(s.toInt()) }
    )
    return dispatch[key]?.invoke(x) ?: 0
}

fun quirkInfix(x: String): Int = (x to 0).coerceFirst()

infix fun Pair<String, Int>.coerceFirst(): Int = this.first.toIntOrNull() ?: this.second
