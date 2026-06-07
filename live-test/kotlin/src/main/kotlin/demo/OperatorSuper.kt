package demo

interface Invokable {
    operator fun invoke(): String
}

class Caller : Invokable {
    override operator fun invoke(): String = "called"
}
