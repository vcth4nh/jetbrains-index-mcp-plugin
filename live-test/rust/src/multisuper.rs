pub trait MyTrait {
    fn m(&self) -> String;
}

pub struct MyStruct;

impl MyTrait for MyStruct {
    fn m(&self) -> String {
        "impl".to_string()
    }
}

pub trait MyConst {
    const KIND: &'static str;
}

pub trait MyTypeAlias {
    type Output;
}

impl MyConst for MyStruct {
    const KIND: &'static str = "impl";
}

impl MyTypeAlias for MyStruct {
    type Output = String;
}
