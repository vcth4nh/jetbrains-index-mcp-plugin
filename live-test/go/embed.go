package main

import "fmt"

type Labeled struct {
	baseShape
	label string
}

func (l Labeled) Note() string {
	return fmt.Sprintf("%s: %s", l.label, l.Describe())
}
