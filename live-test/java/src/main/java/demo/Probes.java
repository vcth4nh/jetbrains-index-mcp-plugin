package demo;

class Probe {
    int target() {
        return 42;
    }

    int sameClassCaller() {
        return target() + 1;
    }
}

class ProbeAux {
    static int freeProdCaller() {
        return new Probe().target();
    }
}

class ProbeProdChild extends Probe {
}
