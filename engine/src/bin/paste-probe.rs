use quite_whisper_engine::inserter::TextInserter;
use std::{env, thread, time::Duration};

fn main() -> anyhow::Result<()> {
    let text = env::args()
        .nth(1)
        .unwrap_or_else(|| "QuiteWhisper paste probe".to_string());
    let restore_clipboard = env::args()
        .nth(2)
        .map(|value| value != "false")
        .unwrap_or(false);

    eprintln!("Focus a text field now. Pasting in 3 seconds...");
    thread::sleep(Duration::from_secs(3));
    TextInserter::default().paste_text(&text, restore_clipboard)?;
    eprintln!("Paste command sent.");
    Ok(())
}
