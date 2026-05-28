// Trait method with default body. Struct may override or use default.
pub trait Computable {
    fn compute(&self) -> String {
        "default".to_string()
    }
}

pub struct Overrider;

impl Computable for Overrider {
    fn compute(&self) -> String {
        "overridden".to_string()
    }
}
