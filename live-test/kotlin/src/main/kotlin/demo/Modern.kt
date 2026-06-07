package demo

class Counter {
    private var n = 0
    fun increment() { n++ }
    fun value(): Int = n

    companion object {
        const val DEFAULT_LIMIT = 100
        fun create(): Counter = Counter()
    }
}

suspend fun fetchValue(): Int = 42

suspend fun computeTotal(): Int {
    val a = fetchValue()
    val b = fetchValue()
    return a + b
}

fun useCounter(): Int {
    val c = Counter.create()
    c.increment()
    return Counter.DEFAULT_LIMIT + c.value()
}
