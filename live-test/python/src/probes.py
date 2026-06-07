class Probe:
    def target(self):
        return 42

    def same_class_caller(self):
        return self.target() + 1


def free_prod_caller():
    return Probe().target()


class ProbeProdChild(Probe):
    pass
