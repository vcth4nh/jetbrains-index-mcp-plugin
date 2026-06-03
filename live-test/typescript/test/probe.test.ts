import { Probe } from '../src/probes';

export class ProbeTest {
    testCaller(): number {
        return new Probe().target();
    }
}

export class ProbeTestChild extends Probe {
}
