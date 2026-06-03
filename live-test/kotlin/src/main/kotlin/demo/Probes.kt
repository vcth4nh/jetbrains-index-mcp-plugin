package demo

open class Probe {
    fun target(): Int = 42

    fun sameClassCaller(): Int = target() + 1
}

fun freeProdCaller(): Int = Probe().target()

class ProbeProdChild : Probe()
