use std::io::{self, Read};
fn main() {
    let mut input = String::new();
    if io::stdin().read_to_string(&mut input).is_err() {
        std::process::exit(2);
    }
    match gymnota_history_core::process_json(&input) {
        Ok(out) => println!("{out}"),
        Err(code) => {
            eprintln!("{code}");
            std::process::exit(1);
        }
    }
}
