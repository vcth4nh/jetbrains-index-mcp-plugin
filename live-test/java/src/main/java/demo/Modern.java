package demo;

public class Modern {
    public record Point(int x, int y) {
        public int sum() { return x + y; }
    }

    public sealed interface Animal permits Cat, Dog {
        String name();
    }

    public static final class Cat implements Animal {
        @Override public String name() { return "cat"; }
    }

    public static final class Dog implements Animal {
        @Override public String name() { return "dog"; }
    }

    public static int probe() {
        Point p = new Point(3, 4);
        return p.sum();
    }
}
