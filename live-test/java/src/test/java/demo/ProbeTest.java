package demo;

class ProbeTest {
    void testCaller() {
        new Probe().target();
    }
}

class ProbeTestChild extends Probe {
}
