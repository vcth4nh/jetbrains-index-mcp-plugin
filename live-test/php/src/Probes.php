<?php
namespace Demo;

class Probe
{
    public function target(): int
    {
        return 42;
    }

    public function sameClassCaller(): int
    {
        return $this->target() + 1;
    }
}

function freeProdCaller(): int
{
    return (new Probe())->target();
}

class ProbeProdChild extends Probe
{
}
