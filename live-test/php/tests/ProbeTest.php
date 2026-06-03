<?php
namespace Demo\Tests;

use Demo\Probe;

final class ProbeTest
{
    public function testCaller(): int
    {
        return (new Probe())->target();
    }
}

class ProbeTestChild extends Probe
{
}
