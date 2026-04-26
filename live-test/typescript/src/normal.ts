export interface Drawable {
    draw(): string;
}

export abstract class Shape {
    abstract area(): number;

    describe(): string {
        return `${this.constructor.name} with area ${this.area()}`;
    }
}

export class Circle extends Shape implements Drawable {
    constructor(public readonly radius: number) {
        super();
    }
    area(): number { return 3.14159 * this.radius * this.radius; }
    draw(): string { return `circle r=${this.radius}`; }
}

export class Rectangle extends Shape implements Drawable {
    constructor(public readonly width: number, public readonly height: number) {
        super();
    }
    area(): number { return this.width * this.height; }
    draw(): string { return `rect ${this.width}x${this.height}`; }
}

export class Square extends Rectangle {
    constructor(side: number) {
        super(side, side);
    }
}

export class ShapeCollection {
    readonly shapes: Shape[] = [];

    add(shape: Shape): void { this.shapes.push(shape); }

    totalArea(): number {
        let sum = 0;
        for (const s of this.shapes) sum += s.area();
        return sum;
    }

    largest(): Shape | null {
        let best: Shape | null = null;
        for (const s of this.shapes) {
            if (best === null || s.area() > best.area()) best = s;
        }
        return best;
    }
}

export function makeDefaultShapes(): Shape[] {
    return [new Circle(1.0), new Rectangle(2.0, 3.0), new Square(4.0)];
}
