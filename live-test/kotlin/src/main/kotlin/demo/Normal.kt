package demo

interface Drawable {
    fun draw(): String
}

abstract class Shape {
    abstract fun area(): Double

    open fun describe(): String = "${this::class.simpleName} with area ${area()}"
}

class Circle(val radius: Double) : Shape(), Drawable {
    override fun area(): Double = 3.14159 * radius * radius
    override fun draw(): String = "circle r=$radius"
}

open class Rectangle(val width: Double, val height: Double) : Shape(), Drawable {
    override fun area(): Double = width * height
    override fun draw(): String = "rect ${width}x$height"
}

class Square(side: Double) : Rectangle(side, side)

class ShapeCollection {
    val shapes: MutableList<Shape> = mutableListOf()

    fun add(shape: Shape) {
        shapes.add(shape)
    }

    fun totalArea(): Double = shapes.sumOf { it.area() }

    fun largest(): Shape? = shapes.maxByOrNull { it.area() }
}

fun makeDefaultShapes(): List<Shape> = listOf(Circle(1.0), Rectangle(2.0, 3.0), Square(4.0))
