package demo;

class LambdaHost {
    Runnable make() {
        return () -> System.out.println("lambda");
    }
}
