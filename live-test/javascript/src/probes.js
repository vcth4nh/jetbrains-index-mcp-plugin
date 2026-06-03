'use strict';

class Probe {
    target() {
        return 42;
    }

    sameClassCaller() {
        return this.target() + 1;
    }
}

function freeProdCaller() {
    return new Probe().target();
}

class ProbeProdChild extends Probe {
}

module.exports = { Probe, freeProdCaller, ProbeProdChild };
