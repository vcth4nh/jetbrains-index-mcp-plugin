macro_rules! square {
    ($x:expr) => {
        $x * $x
    };
}

#[derive(Debug, Clone, PartialEq)]
pub struct Point {
    pub x: i32,
    pub y: i32,
}

impl Point {
    pub fn origin() -> Self {
        Point { x: 0, y: 0 }
    }
}

pub fn demo() -> i32 {
    let p = Point::origin();
    let q = p.clone();
    let _eq = p == q;
    square!(p.x) + square!(q.y)
}
