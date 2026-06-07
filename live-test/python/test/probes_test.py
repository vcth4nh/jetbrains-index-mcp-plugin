from probes import Probe


def test_caller():
    Probe().target()


class ProbeTestChild(Probe):
    pass
