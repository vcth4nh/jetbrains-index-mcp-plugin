export enum Direction {
    North,
    East,
    South,
    West,
}

export enum Color {
    Red = "red",
    Green = "green",
    Blue = "blue",
}

export function opposite(d: Direction): Direction {
    if (d === Direction.North) return Direction.South;
    if (d === Direction.South) return Direction.North;
    if (d === Direction.East) return Direction.West;
    return Direction.East;
}

export function isWarm(c: Color): boolean {
    return c === Color.Red || c === Color.Green;
}
