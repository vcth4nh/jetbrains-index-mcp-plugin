pub trait Drawable {
    fn draw(&self) -> String;
}

pub trait Shape {
    fn area(&self) -> f64;

    fn describe(&self) -> String {
        format!("Shape with area {}", self.area())
    }
}

pub struct Circle {
    pub radius: f64,
}

impl Shape for Circle {
    fn area(&self) -> f64 {
        3.14159 * self.radius * self.radius
    }

    fn describe(&self) -> String {
        format!("Circle with area {}", self.area())
    }
}

impl Drawable for Circle {
    fn draw(&self) -> String {
        format!("circle r={}", self.radius)
    }
}

pub struct Rectangle {
    pub width: f64,
    pub height: f64,
}

impl Shape for Rectangle {
    fn area(&self) -> f64 {
        self.width * self.height
    }

    fn describe(&self) -> String {
        format!("Rectangle with area {}", self.area())
    }
}

impl Drawable for Rectangle {
    fn draw(&self) -> String {
        format!("rect {}x{}", self.width, self.height)
    }
}

pub struct Square {
    inner: Rectangle,
}

impl Square {
    pub fn new(side: f64) -> Self {
        Square { inner: Rectangle { width: side, height: side } }
    }
}

impl Shape for Square {
    fn area(&self) -> f64 {
        self.inner.area()
    }
}

pub struct ShapeCollection {
    pub shapes: Vec<Box<dyn Shape>>,
}

impl ShapeCollection {
    pub fn new() -> Self {
        ShapeCollection { shapes: Vec::new() }
    }

    pub fn add(&mut self, shape: Box<dyn Shape>) {
        self.shapes.push(shape);
    }

    pub fn total_area(&self) -> f64 {
        self.shapes.iter().map(|s| s.area()).sum()
    }

    pub fn largest(&self) -> Option<&Box<dyn Shape>> {
        self.shapes.iter().max_by(|a, b| {
            a.area().partial_cmp(&b.area()).unwrap()
        })
    }
}

pub fn make_default_shapes() -> Vec<Box<dyn Shape>> {
    vec![
        Box::new(Circle { radius: 1.0 }),
        Box::new(Rectangle { width: 2.0, height: 3.0 }),
        Box::new(Square::new(4.0)),
    ]
}
