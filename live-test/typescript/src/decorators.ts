export function traced(target: Function, context: any): void {
    void target;
    void context;
}

export class Service {
    @traced
    greet(name: string): string {
        return `hello ${name}`;
    }

    @traced
    farewell(name: string): string {
        return `bye ${name}`;
    }
}
