"""Multi-super fixture: one method overrides three abstract supers at once."""
from abc import ABC, abstractmethod
from typing import Protocol


class IRender(ABC):
    @abstractmethod
    def name(self) -> str:
        ...


class IDisplay(ABC):
    @abstractmethod
    def name(self) -> str:
        ...


class Base(ABC):
    @abstractmethod
    def name(self) -> str:
        ...


class Triple(Base, IRender, IDisplay):
    def name(self) -> str:
        return "triple"


class IRunnable(ABC):
    @abstractmethod
    def m(self) -> str:
        ...


class DeepBase(ABC):
    @abstractmethod
    def m(self) -> str:
        ...


class DeepMid1(DeepBase):
    def m(self) -> str:
        return "mid1"


class DeepMid2(DeepMid1, IRunnable):
    def m(self) -> str:
        return "mid2"


class DeepLeaf(DeepMid2):
    def m(self) -> str:
        return "leaf"


class DiamondTop(ABC):
    @abstractmethod
    def m(self) -> str:
        ...


class DiamondLeft(DiamondTop):
    def m(self) -> str:
        return "left"


class DiamondRight(DiamondTop):
    def m(self) -> str:
        return "right"


class DiamondCenter(DiamondTop):
    def m(self) -> str:
        return "center"


class DiamondBottom(DiamondLeft, DiamondRight, DiamondCenter):
    def m(self) -> str:
        return "bottom"


class DrawableProto(Protocol):
    def draw(self) -> str:
        ...


class CanvasProto(Protocol):
    def draw(self) -> str:
        ...


class AbstractDrawable(ABC):
    @abstractmethod
    def draw(self) -> str:
        ...


class ConcreteShape(AbstractDrawable, CanvasProto):
    def draw(self) -> str:
        return "shape"


class LogBase:
    def log(self, x: object) -> None:
        print(x)


class LoggingMixin:
    def log(self, x: object) -> None:
        print(x)


class ExtraLogger:
    def log(self, x: object) -> None:
        print(x)


class MixedClass(LogBase, LoggingMixin, ExtraLogger):
    def log(self, x: object) -> None:
        print(f"mixed: {x}")


class PropBase:
    @property
    def value(self) -> int:
        return 0


class ValueA:
    @property
    def value(self) -> int:
        return 1


class ValueB:
    @property
    def value(self) -> int:
        return 2


class PropDerived(PropBase, ValueA, ValueB):
    @property
    def value(self) -> int:
        return 3


class FactoryBase:
    @classmethod
    def factory(cls) -> "FactoryBase":
        return cls()


class FactoryAlt:
    @classmethod
    def factory(cls) -> "FactoryAlt":
        return cls()


class FactoryExtra:
    @classmethod
    def factory(cls) -> "FactoryExtra":
        return cls()


class FactoryDerived(FactoryBase, FactoryAlt, FactoryExtra):
    @classmethod
    def factory(cls) -> "FactoryBase":
        return cls()


class StaticBase:
    @staticmethod
    def helper() -> str:
        return "base"


class StaticAlt:
    @staticmethod
    def helper() -> str:
        return "alt"


class StaticExtra:
    @staticmethod
    def helper() -> str:
        return "extra"


class StaticDerived(StaticBase, StaticAlt, StaticExtra):
    @staticmethod
    def helper() -> str:
        return "derived"
