package main

// Standalone struct method with a uniquely-named method — no interface in
// this file declares Compute, so no super (Go uses implicit interfaces).
type Standalone struct{}

func (s *Standalone) Compute() string {
	return "standalone"
}
