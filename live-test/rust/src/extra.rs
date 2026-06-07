pub mod inner {
    pub fn nested_helper() -> i32 { 42 }
    pub struct Marker;
}

pub fn extra_function(s: &str) -> i32 {
    inner::nested_helper() + s.len() as i32
}

pub fn use_quirks_circle() -> f64 {
    crate::normal::Circle { radius: 1.0 }.radius
}
