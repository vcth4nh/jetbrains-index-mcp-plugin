<?php
namespace Demo;

enum Status: string {
    case Active = 'A';
    case Inactive = 'I';

    public function label(): string {
        return match($this) {
            Status::Active => 'Active',
            Status::Inactive => 'Inactive',
        };
    }
}

enum Color {
    case Red;
    case Green;
}

function defaultStatus(): Status {
    return Status::Active;
}
