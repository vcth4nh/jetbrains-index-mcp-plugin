'use strict';

class BaseMix {
    greet() { return "base"; }
}

class MidMix extends BaseMix {
    greet() { return "mid"; }
}

class LeafMix extends MidMix {
    greet() { return "leaf"; }
}

module.exports = { BaseMix, MidMix, LeafMix };
