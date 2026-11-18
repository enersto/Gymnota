#[cfg(feature = "catalog-sqlite")]
fn main() -> Result<(), &'static str> {
    let input = std::io::read_to_string(std::io::stdin()).map_err(|_| "stdin_failed")?;
    println!(
        "{}",
        match std::env::args().nth(1).as_deref() {
            Some("--mutate-copy") => gymnota_history_core::routine::mutate_copy(&input)?,
            Some("--workout-copy") => gymnota_history_core::workout_store::execute(&input)?,
            Some("--bootstrap") => gymnota_history_core::bootstrap::execute(&input)?,
            Some("--backup") => gymnota_history_core::backup::execute(&input)?,
            Some("--live-query") => gymnota_history_core::live_store::execute(&input)?,
            Some("--history-db") =>
                gymnota_history_core::history_store::snapshot(input.trim())?.to_string(),
            Some("--history-store") => gymnota_history_core::history_store::process(input.trim())?,
            Some("--history") => gymnota_history_core::process_json(&input)?,
            _ => gymnota_history_core::routine::execute(&input)?,
        }
    );
    Ok(())
}

#[cfg(not(feature = "catalog-sqlite"))]
fn main() {
    eprintln!("Enable catalog-sqlite to read a routine database.");
    std::process::exit(2);
}
