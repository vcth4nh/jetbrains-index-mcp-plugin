<?php
namespace Demo;

trait RequiresImpl {
    abstract public function required(): string;
}

class Implementer {
    use RequiresImpl;

    public function required(): string {
        return "impl";
    }
}
