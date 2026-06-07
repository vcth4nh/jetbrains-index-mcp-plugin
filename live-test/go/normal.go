package main

import "fmt"

type Drawable interface {
	Draw() string
}

type Shape interface {
	Area() float64
	Describe() string
}

type baseShape struct{}

func (b baseShape) Describe() string { return "shape with unknown area" }

type Circle struct {
	baseShape
	Radius float64
}

func (c Circle) Area() float64 { return 3.14159 * c.Radius * c.Radius }
func (c Circle) Describe() string {
	return fmt.Sprintf("Circle with area %f", c.Area())
}
func (c Circle) Draw() string { return fmt.Sprintf("circle r=%f", c.Radius) }

type Rectangle struct {
	baseShape
	Width, Height float64
}

func (r Rectangle) Area() float64 { return r.Width * r.Height }
func (r Rectangle) Describe() string {
	return fmt.Sprintf("Rectangle with area %f", r.Area())
}
func (r Rectangle) Draw() string { return fmt.Sprintf("rect %fx%f", r.Width, r.Height) }

type Square struct{ Rectangle }

func NewSquare(side float64) Square {
	return Square{Rectangle: Rectangle{Width: side, Height: side}}
}

type ShapeCollection struct {
	Shapes []Shape
}

func (sc *ShapeCollection) Add(s Shape) {
	sc.Shapes = append(sc.Shapes, s)
}

func (sc *ShapeCollection) TotalArea() float64 {
	sum := 0.0
	for _, s := range sc.Shapes {
		sum += s.Area()
	}
	return sum
}

func (sc *ShapeCollection) Largest() Shape {
	var best Shape
	for _, s := range sc.Shapes {
		if best == nil || s.Area() > best.Area() {
			best = s
		}
	}
	return best
}

func MakeDefaultShapes() []Shape {
	return []Shape{
		Circle{Radius: 1.0},
		Rectangle{Width: 2.0, Height: 3.0},
		NewSquare(4.0),
	}
}
