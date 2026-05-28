<?php
namespace Demo;

abstract class StaticBase {
    abstract public static function make(): string;
}

class StaticDerived extends StaticBase {
    public static function make(): string {
        return "child";
    }
}
