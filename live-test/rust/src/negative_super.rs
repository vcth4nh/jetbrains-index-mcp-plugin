// Inherent impl (no trait) — fn has no super in any trait.
pub struct Inherent;

impl Inherent {
    pub fn foo(&self) -> String {
        "inherent".to_string()
    }
}
