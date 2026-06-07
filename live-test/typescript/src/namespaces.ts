export namespace Geometry {
    export class Point {
        constructor(public readonly x: number, public readonly y: number) {}

        distanceTo(other: Point): number {
            return Math.hypot(this.x - other.x, this.y - other.y);
        }
    }

    export function origin(): Point {
        return new Point(0, 0);
    }
}

// declaration merging augments Geometry
export namespace Geometry {
    export function unit(): Point {
        return new Point(1, 0);
    }
}

export function span(): number {
    return Geometry.origin().distanceTo(Geometry.unit());
}
