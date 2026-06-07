"""Dataclass __post_init__ override across base/derived."""
from dataclasses import dataclass


@dataclass
class ParentDC:
    name: str

    def __post_init__(self) -> None:
        self.name = self.name.lower()


@dataclass
class ChildDC(ParentDC):
    extra: str = ""

    def __post_init__(self) -> None:
        super().__post_init__()
        self.extra = self.extra.upper()
