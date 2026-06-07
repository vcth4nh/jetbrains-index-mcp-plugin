package demo

interface CompFactory {
    fun make(): String
}

interface KindBearer {
    val KIND: String
}

abstract class CompParent {
    companion object : CompFactory, KindBearer {
        override fun make(): String = "parent"
        override val KIND: String = "parent"
    }
}

class CompChild : CompParent() {
    companion object : CompFactory, KindBearer {
        override fun make(): String = "child"
        override val KIND: String = "child"
    }
}
