use arboard::Clipboard;
use enigo::{Enigo, Key, KeyboardControllable};
use std::{thread, time::Duration};

const CLIPBOARD_SETTLE_DELAY: Duration = Duration::from_millis(120);
const CLIPBOARD_RESTORE_DELAY: Duration = Duration::from_millis(900);
const KEY_EVENT_DELAY: Duration = Duration::from_millis(25);

#[derive(Default)]
pub struct TextInserter;

impl TextInserter {
    pub fn paste_text(&self, text: &str, restore_clipboard: bool) -> anyhow::Result<()> {
        paste_with_ports(
            text,
            restore_clipboard,
            &mut SystemClipboard::new()?,
            &mut SystemKeyboard::default(),
        )
    }
}

trait ClipboardPort {
    fn get_text(&mut self) -> anyhow::Result<Option<String>>;
    fn set_text(&mut self, text: &str) -> anyhow::Result<()>;
}

trait KeyboardPort {
    fn paste(&mut self) -> anyhow::Result<()>;
}

fn paste_with_ports(
    text: &str,
    restore_clipboard: bool,
    clipboard: &mut dyn ClipboardPort,
    keyboard: &mut dyn KeyboardPort,
) -> anyhow::Result<()> {
    let previous = if restore_clipboard {
        clipboard.get_text()?
    } else {
        None
    };

    clipboard.set_text(text)?;
    thread::sleep(CLIPBOARD_SETTLE_DELAY);
    keyboard.paste()?;

    if restore_clipboard {
        thread::sleep(CLIPBOARD_RESTORE_DELAY);
        if let Some(previous) = previous {
            clipboard.set_text(&previous)?;
        }
    }

    Ok(())
}

struct SystemClipboard {
    clipboard: Clipboard,
}

impl SystemClipboard {
    fn new() -> anyhow::Result<Self> {
        Ok(Self {
            clipboard: Clipboard::new()?,
        })
    }
}

impl ClipboardPort for SystemClipboard {
    fn get_text(&mut self) -> anyhow::Result<Option<String>> {
        match self.clipboard.get_text() {
            Ok(text) => Ok(Some(text)),
            Err(_) => Ok(None),
        }
    }

    fn set_text(&mut self, text: &str) -> anyhow::Result<()> {
        self.clipboard.set_text(text.to_string())?;
        Ok(())
    }
}

#[derive(Default)]
struct SystemKeyboard {
    enigo: Enigo,
}

impl KeyboardPort for SystemKeyboard {
    fn paste(&mut self) -> anyhow::Result<()> {
        #[cfg(target_os = "macos")]
        let modifier = Key::Meta;
        #[cfg(not(target_os = "macos"))]
        let modifier = Key::Control;
        #[cfg(target_os = "windows")]
        let paste_key = Key::V;
        #[cfg(not(target_os = "windows"))]
        let paste_key = Key::Layout('v');

        self.enigo.key_down(modifier);
        thread::sleep(KEY_EVENT_DELAY);
        self.enigo.key_click(paste_key);
        thread::sleep(KEY_EVENT_DELAY);
        self.enigo.key_up(modifier);
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[derive(Default)]
    struct FakeClipboard {
        text: Option<String>,
        writes: Vec<String>,
    }

    impl ClipboardPort for FakeClipboard {
        fn get_text(&mut self) -> anyhow::Result<Option<String>> {
            Ok(self.text.clone())
        }

        fn set_text(&mut self, text: &str) -> anyhow::Result<()> {
            self.text = Some(text.to_string());
            self.writes.push(text.to_string());
            Ok(())
        }
    }

    #[derive(Default)]
    struct FakeKeyboard {
        pasted: bool,
    }

    impl KeyboardPort for FakeKeyboard {
        fn paste(&mut self) -> anyhow::Result<()> {
            self.pasted = true;
            Ok(())
        }
    }

    #[test]
    fn paste_restores_previous_clipboard_text() {
        let mut clipboard = FakeClipboard {
            text: Some("old".to_string()),
            writes: Vec::new(),
        };
        let mut keyboard = FakeKeyboard::default();

        paste_with_ports("new", true, &mut clipboard, &mut keyboard).unwrap();

        assert!(keyboard.pasted);
        assert_eq!(clipboard.text.as_deref(), Some("old"));
        assert_eq!(clipboard.writes, vec!["new", "old"]);
    }

    #[test]
    fn paste_can_leave_text_in_clipboard() {
        let mut clipboard = FakeClipboard {
            text: Some("old".to_string()),
            writes: Vec::new(),
        };
        let mut keyboard = FakeKeyboard::default();

        paste_with_ports("new", false, &mut clipboard, &mut keyboard).unwrap();

        assert!(keyboard.pasted);
        assert_eq!(clipboard.text.as_deref(), Some("new"));
        assert_eq!(clipboard.writes, vec!["new"]);
    }
}
