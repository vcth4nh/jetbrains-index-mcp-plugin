export class Probe {
    target(): number {
        return 42;
    }

    sameClassCaller(): number {
        return this.target() + 1;
    }
}

export function freeProdCaller(): number {
    return new Probe().target();
}

export class ProbeProdChild extends Probe {
}
