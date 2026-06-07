pub struct Probe;

impl Probe {
    pub fn target(&self) -> i32 {
        42
    }

    pub fn same_class_caller(&self) -> i32 {
        self.target() + 1
    }
}

pub fn free_prod_caller() -> i32 {
    Probe.target()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_caller() {
        assert_eq!(Probe.target(), 42);
    }
}
