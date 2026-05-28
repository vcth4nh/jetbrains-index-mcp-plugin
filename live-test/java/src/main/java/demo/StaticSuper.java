package demo;

class StaticBase {
    static String factory() {
        return "base";
    }
}

class StaticDerived extends StaticBase {
    static String factory() {
        return "child";
    }
}
