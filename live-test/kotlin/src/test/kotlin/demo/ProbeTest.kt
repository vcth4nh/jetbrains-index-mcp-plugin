package demo

class ProbeTest {
    fun testCaller() {
        Probe().target()
    }
}

class ProbeTestChild : Probe()
