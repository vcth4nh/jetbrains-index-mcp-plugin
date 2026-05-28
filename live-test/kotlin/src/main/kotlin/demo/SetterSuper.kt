package demo

interface WithProp {
    var value: String
}

class WithSetter : WithProp {
    override var value: String = "init"
        get() = field
        set(v) { field = v.uppercase() }
}
