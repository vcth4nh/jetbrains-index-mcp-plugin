'use strict';

// Deep override chain (3 levels): Leaf extends Mid extends Base.
// Exercises transitivity — Leaf.m has Mid.m as direct super, Base.m as transitive.
class Base {
    m() { return "base"; }
}

class Mid extends Base {
    m() { return "mid"; }
}

class Leaf extends Mid {
    m() { return "leaf"; }
}

// Accessor (get) override across single-inheritance edge.
class AccessorBase {
    get value() { return "base"; }
}

class AccessorDerived extends AccessorBase {
    get value() { return "derived"; }
}

// Static method override via prototype chain.
class StaticBase {
    static factory() { return "base"; }
}

class StaticDerived extends StaticBase {
    static factory() { return "derived"; }
}

module.exports = {
    Base, Mid, Leaf,
    AccessorBase, AccessorDerived,
    StaticBase, StaticDerived,
};
