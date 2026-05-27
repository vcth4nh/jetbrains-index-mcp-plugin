package demo;

interface IRender {
    String name();
}

interface IDisplay {
    String name();
}

abstract class Base {
    abstract String name();
}

class Triple extends Base implements IRender, IDisplay {
    @Override
    public String name() {
        return "triple";
    }
}

abstract class ChainBase {
    abstract String tag();
}

class ChainMid1 extends ChainBase {
    @Override
    String tag() {
        return "mid1";
    }
}

interface ITaggable {
    String tag();
}

class ChainMid2 extends ChainMid1 implements ITaggable {
    @Override
    public String tag() {
        return "mid2";
    }
}

class ChainLeaf extends ChainMid2 {
    @Override
    public String tag() {
        return "leaf";
    }
}

interface DiamondTop {
    String pick();
}

interface DiamondLeft extends DiamondTop {
}

interface DiamondRight extends DiamondTop {
}

abstract class DiamondLegacy {
    abstract String pick();
}

class DiamondBottom extends DiamondLegacy implements DiamondLeft, DiamondRight {
    @Override
    public String pick() {
        return "bottom";
    }
}

interface Greeter {
    default String greet() {
        return "hello";
    }
}

interface FriendlyGreeter {
    default String greet() {
        return "hi";
    }
}

abstract class AbstractGreeter {
    abstract String greet();
}

class LoudGreeter extends AbstractGreeter implements Greeter, FriendlyGreeter {
    @Override
    public String greet() {
        return "HELLO";
    }
}

sealed interface SealedShape permits SealedSquare, SealedTriangle {
    int sides();
}

interface Polygon {
    int sides();
}

interface Quadrilateral {
    int sides();
}

final class SealedSquare implements SealedShape, Polygon, Quadrilateral {
    @Override
    public int sides() {
        return 4;
    }
}

final class SealedTriangle implements SealedShape, Polygon {
    @Override
    public int sides() {
        return 3;
    }
}

interface Named {
    String label();
}

interface Tagged {
    String label();
}

interface Identified {
    String label();
}

record LabelPoint(String label, int x, int y) implements Named, Tagged, Identified {
}

interface IntFn {
    int apply(int x);
}

interface IntOp {
    int apply(int x);
}

enum Op implements IntFn, IntOp {
    ADD {
        @Override
        public int apply(int x) {
            return x + 1;
        }
    },
    SUB {
        @Override
        public int apply(int x) {
            return x - 1;
        }
    };

    public abstract int apply(int x);
}

class AnonHost {
    Runnable make() {
        // Anonymous classes cannot have multiple direct supers in Java — kept as 1-super negative anchor
        return new Runnable() {
            @Override
            public void run() {
                System.out.println("anon");
            }
        };
    }
}
