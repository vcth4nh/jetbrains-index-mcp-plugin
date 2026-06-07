"""Vanilla OOP patterns for the live MCP test harness."""
from abc import ABC, abstractmethod
from typing import Protocol


class Drawable(Protocol):
    def draw(self) -> str: ...


class Shape(ABC):
    @abstractmethod
    def area(self) -> float:
        ...

    def describe(self) -> str:
        return f"{type(self).__name__} with area {self.area()}"


class Circle(Shape):
    def __init__(self, radius: float) -> None:
        self.radius = radius

    def area(self) -> float:
        return 3.14159 * self.radius * self.radius

    def draw(self) -> str:
        return f"circle r={self.radius}"


class Rectangle(Shape):
    def __init__(self, width: float, height: float) -> None:
        self.width = width
        self.height = height

    def area(self) -> float:
        return self.width * self.height

    def draw(self) -> str:
        return f"rect {self.width}x{self.height}"


class Square(Rectangle):
    def __init__(self, side: float) -> None:
        super().__init__(side, side)


class ShapeCollection:
    def __init__(self) -> None:
        self.shapes: list[Shape] = []

    def add(self, shape: Shape) -> None:
        self.shapes.append(shape)

    def total_area(self) -> float:
        return sum(s.area() for s in self.shapes)

    def largest(self) -> Shape | None:
        if not self.shapes:
            return None
        return max(self.shapes, key=lambda s: s.area())


def make_default_shapes() -> list[Shape]:
    return [Circle(1.0), Rectangle(2.0, 3.0), Square(4.0)]
