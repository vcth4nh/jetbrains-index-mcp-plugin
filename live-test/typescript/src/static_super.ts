export class StaticBase {
    static factory(): string { return "base"; }
}

export class Child extends StaticBase {
    static factory(): string { return "child"; }
}
