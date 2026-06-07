'use strict';

const Amplifier = (Base) => class extends Base {
    shout() { return "SHOUT"; }
};

class Plain {
    shout() { return "plain"; }
}

class WithMixin extends Amplifier(Plain) {
    shout() { return "with-mixin"; }
}

module.exports = { Amplifier, Plain, WithMixin };
