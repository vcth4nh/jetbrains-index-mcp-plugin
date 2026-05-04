package demo;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class Quirks {

    public static int quirkLambda(String x) {
        Function<String, Integer> fn = s -> Integer.parseInt(s);
        return fn.apply(x);
    }

    public static int quirkVar(String x) {
        var coerce = (Function<String, Integer>) Integer::parseInt;
        return coerce.apply(x);
    }

    public static int quirkAnonClass(String x) {
        Function<String, Integer> fn = new Function<>() {
            @Override
            public Integer apply(String s) {
                return Integer.parseInt(s);
            }
        };
        return fn.apply(x);
    }

    public static Optional<Integer> quirkOptional(String x) {
        return Optional.of(x).map(Integer::parseInt);
    }

    public static int quirkTernary(String x, boolean stripPlus) {
        Function<String, Integer> fn = stripPlus
            ? s -> Integer.parseInt(s.replace("+", ""))
            : Integer::parseInt;
        return fn.apply(x);
    }

    public static CompletableFuture<Integer> quirkCompletableFuture(String x) {
        return CompletableFuture.supplyAsync(() -> Integer.parseInt(x));
    }

    public static List<Integer> quirkStreamMap(List<String> xs) {
        return xs.stream().map(Integer::parseInt).collect(Collectors.toList());
    }

    public static int quirkMapDispatch(String key, String x) {
        Map<String, Function<String, Integer>> dispatch = new HashMap<>();
        dispatch.put("int", Integer::parseInt);
        dispatch.put("abs", s -> Math.abs(Integer.parseInt(s)));
        return dispatch.get(key).apply(x);
    }

    static class Coercer {
        private final String prefix;
        Coercer(String prefix) { this.prefix = prefix; }
        int coerce(String x) { return Integer.parseInt(x.replace(prefix, "")); }
    }

    @FunctionalInterface
    interface Coerce { int run(String s); }

    public static int quirkFunctionalIface(String x) {
        Coerce c = Integer::parseInt;
        return c.run(x);
    }

    enum CoerceMode {
        INT { int apply(String s) { return Integer.parseInt(s); } },
        ABS { int apply(String s) { return Math.abs(Integer.parseInt(s)); } };
        abstract int apply(String s);
    }

    public static int quirkEnumDispatch(String x) {
        return CoerceMode.INT.apply(x);
    }

    public static int quirkSupplier(String x) {
        Supplier<Integer> supplier = () -> Integer.parseInt(x);
        return supplier.get();
    }
}
