use pulldown_cmark::{Event, Options, Parser, Tag};
fn main() {
    for text in ["~~~text\nhttps://example.com/path\n~~~\n", "    https://example.com/path\n", "```text\nhttps://example.com/path\n", "``code ` https://example.com/path``"] {
        println!("input={:?}",text);
        for (event, range) in Parser::new_ext(text, Options::empty()).into_offset_iter() {
            if matches!(event, Event::Code(_) | Event::Start(Tag::CodeBlock(_))) {
                println!("{:?} {:?} original={:?}",event,range,&text[range.clone()]);
            }
        }
    }
}
