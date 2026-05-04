package demo;

import java.util.ArrayList;
import java.util.List;

interface Drawable {
    String draw();
}

abstract class Shape {
    abstract double area();

    String describe() {
        return getClass().getSimpleName() + " with area " + area();
    }
}

class Circle extends Shape implements Drawable {
    private final double radius;

    Circle(double radius) {
        this.radius = radius;
    }

    @Override
    double area() {
        return 3.14159 * radius * radius;
    }

    @Override
    public String draw() {
        return "circle r=" + radius;
    }
}

class Rectangle extends Shape implements Drawable {
    protected final double width;
    protected final double height;

    Rectangle(double width, double height) {
        this.width = width;
        this.height = height;
    }

    @Override
    double area() {
        return width * height;
    }

    @Override
    public String draw() {
        return "rect " + width + "x" + height;
    }
}

class Square extends Rectangle {
    Square(double side) {
        super(side, side);
    }
}

class ShapeCollection {
    private final List<Shape> shapes = new ArrayList<>();

    void add(Shape shape) {
        shapes.add(shape);
    }

    double totalArea() {
        double sum = 0;
        for (Shape s : shapes) {
            sum += s.area();
        }
        return sum;
    }

    Shape largest() {
        Shape best = null;
        for (Shape s : shapes) {
            if (best == null || s.area() > best.area()) best = s;
        }
        return best;
    }
}

public class Normal {
    public static List<Shape> makeDefaultShapes() {
        List<Shape> shapes = new ArrayList<>();
        shapes.add(new Circle(1.0));
        shapes.add(new Rectangle(2.0, 3.0));
        shapes.add(new Square(4.0));
        return shapes;
    }
}
