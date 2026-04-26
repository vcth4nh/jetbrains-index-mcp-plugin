'use strict';

class Drawable {
    draw() { throw new Error('not implemented'); }
}

class Shape {
    area() { throw new Error('abstract'); }
    describe() { return `${this.constructor.name} with area ${this.area()}`; }
}

class Circle extends Shape {
    constructor(radius) {
        super();
        this.radius = radius;
    }
    area() { return 3.14159 * this.radius * this.radius; }
    draw() { return `circle r=${this.radius}`; }
}

class Rectangle extends Shape {
    constructor(width, height) {
        super();
        this.width = width;
        this.height = height;
    }
    area() { return this.width * this.height; }
    draw() { return `rect ${this.width}x${this.height}`; }
}

class Square extends Rectangle {
    constructor(side) {
        super(side, side);
    }
}

class ShapeCollection {
    constructor() {
        this.shapes = [];
    }
    add(shape) { this.shapes.push(shape); }
    totalArea() {
        let sum = 0;
        for (const s of this.shapes) sum += s.area();
        return sum;
    }
    largest() {
        let best = null;
        for (const s of this.shapes) {
            if (best === null || s.area() > best.area()) best = s;
        }
        return best;
    }
}

function makeDefaultShapes() {
    return [new Circle(1.0), new Rectangle(2.0, 3.0), new Square(4.0)];
}

module.exports = { Drawable, Shape, Circle, Rectangle, Square, ShapeCollection, makeDefaultShapes };
