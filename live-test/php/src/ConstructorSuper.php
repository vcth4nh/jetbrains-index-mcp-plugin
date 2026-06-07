<?php
namespace Demo;

class ParentCtor {
    public function __construct(public string $name) {}
}

class ChildCtor extends ParentCtor {
    public function __construct(string $name, public int $extra) {
        parent::__construct($name);
    }
}
