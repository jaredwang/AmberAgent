use pulldown_cmark::{Options, Parser};
fn main() {
    for text in ["<div><a\n href=\"javascript:alert(1)\">click</a></div>", "<a\n href=\"javascript:alert(1)\">click</a>"] {
        println!("input={:?}", text);
        for event in Parser::new_ext(text, Options::empty()) { println!("{:?}", event); }
    }
}
