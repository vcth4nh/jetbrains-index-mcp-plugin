package demo;

class StaticBase {
    static String factory() {
        return "base";
    }
}

class StaticChild extends StaticBase {
    static String factory() {
        return "child";
    }
}
