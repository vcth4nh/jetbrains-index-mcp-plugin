pub trait MyTrait {
    fn m(&self) -> String;
}

pub struct MyStruct;

impl MyTrait for MyStruct {
    fn m(&self) -> String {
        "impl".to_string()
    }
}
