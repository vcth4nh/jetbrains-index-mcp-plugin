<?php
namespace Demo;

class Quirks {

    public static function qNameRebind(string $x): int {
        $fn = 'intval';
        return $fn($x);
    }

    public static function qVariableFunction(string $x): int {
        $fname = 'intval';
        return $fname($x);
    }

    public static function qClosure(string $x): int {
        $coerce = function (string $s): int { return intval($s); };
        return $coerce($x);
    }

    public static function qArrowFn(string $x): int {
        $coerce = fn(string $s): int => intval($s);
        return $coerce($x);
    }

    public static function qArrayDispatch(string $key, string $x): int {
        $dispatch = ['int' => 'intval', 'len' => 'strlen'];
        $fn = $dispatch[$key];
        return $fn($x);
    }

    public static function qCallableArray(string $x): int {
        $callable = [self::class, 'qNameRebind'];
        return call_user_func($callable, $x);
    }

    public static function qCallUserFunc(string $x): int {
        return call_user_func('intval', $x);
    }

    public static function qStaticMethodVariable(string $x): int {
        $cls = self::class;
        return $cls::qNameRebind($x);
    }

    public static function qFromCallable(string $x): int {
        $coerce = \Closure::fromCallable('intval');
        return $coerce($x);
    }

    public static function qTernary(bool $flag, string $x): int {
        $fn = $flag ? 'intval' : 'strlen';
        return $fn($x);
    }

    public static function qNullCoalesce(string $x): int {
        $fn = null ?? 'intval';
        return $fn($x);
    }

    public static function qMatch(string $mode, string $x): int {
        $fn = match ($mode) {
            'int' => fn($s) => intval($s),
            'len' => 'strlen',
            default => fn($s) => 0,
        };
        return $fn($x);
    }

    public static function qCoerceUsage(Coercer $c, string $x): int {
        return $c->coerce($x);
    }

    public static function qPromotedRead(\Demo\Circle $c): float {
        return $c->radius;
    }
}

interface Coercer {
    public function coerce(string $x): int;
}

class IntCoercer implements Coercer {
    public function coerce(string $x): int { return intval($x); }
}

class LenCoercer implements Coercer {
    public function coerce(string $x): int { return strlen($x); }
}
