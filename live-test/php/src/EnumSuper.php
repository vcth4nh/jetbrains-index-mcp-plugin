<?php
namespace Demo;

interface Labeled {
    public function label(): string;
}

enum Severity: string implements Labeled {
    case Low = "low";
    case High = "high";

    public function label(): string {
        return match ($this) {
            Severity::Low => "Low",
            Severity::High => "High",
        };
    }
}
