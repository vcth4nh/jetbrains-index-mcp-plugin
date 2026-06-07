<?php
namespace Demo;

interface Drawable {
    public function draw(): string;
}

abstract class Shape {
    abstract public function area(): float;

    public function describe(): string {
        return get_class($this) . " with area " . $this->area();
    }
}

class Circle extends Shape implements Drawable {
    public function __construct(public readonly float $radius) {}

    public function area(): float {
        return 3.14159 * $this->radius * $this->radius;
    }

    public function draw(): string {
        return "circle r={$this->radius}";
    }
}

class Rectangle extends Shape implements Drawable {
    public function __construct(public readonly float $width, public readonly float $height) {}

    public function area(): float {
        return $this->width * $this->height;
    }

    public function draw(): string {
        return "rect {$this->width}x{$this->height}";
    }
}

class Square extends Rectangle {
    public function __construct(float $side) {
        parent::__construct($side, $side);
    }
}

class ShapeCollection {
    /** @var Shape[] */
    public array $shapes = [];

    public function add(Shape $shape): void {
        $this->shapes[] = $shape;
    }

    public function totalArea(): float {
        $sum = 0.0;
        foreach ($this->shapes as $s) {
            $sum += $s->area();
        }
        return $sum;
    }

    public function largest(): ?Shape {
        $best = null;
        foreach ($this->shapes as $s) {
            if ($best === null || $s->area() > $best->area()) {
                $best = $s;
            }
        }
        return $best;
    }
}

function makeDefaultShapes(): array {
    return [new Circle(1.0), new Rectangle(2.0, 3.0), new Square(4.0)];
}
