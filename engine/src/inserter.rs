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
            PastePermissions::current(),
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

struct PastePermissions {
    can_post_keyboard_events: bool,
}

impl PastePermissions {
    fn current() -> Self {
        Self {
            can_post_keyboard_events: can_post_keyboard_events(),
        }
    }
}

fn paste_with_ports(
    text: &str,
    restore_clipboard: bool,
    clipboard: &mut dyn ClipboardPort,
    keyboard: &mut dyn KeyboardPort,
    permissions: PastePermissions,
) -> anyhow::Result<()> {
    if !permissions.can_post_keyboard_events {
        anyhow::bail!(
            "macOS Accessibility permission is required to paste text. Grant access to QuiteWhisper, Terminal, or the launched quite-whisper-engine process in System Settings > Privacy & Security > Accessibility, then restart the app."
        );
    }

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

#[cfg(target_os = "macos")]
fn can_post_keyboard_events() -> bool {
    #[link(name = "ApplicationServices", kind = "framework")]
    extern "C" {
        fn AXIsProcessTrusted() -> bool;
    }

    unsafe { AXIsProcessTrusted() }
}

#[cfg(not(target_os = "macos"))]
fn can_post_keyboard_events() -> bool {
    true
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
        let (modifier, paste_key) = paste_shortcut();

        self.enigo.key_down(modifier);
        thread::sleep(KEY_EVENT_DELAY);
        self.enigo.key_click(paste_key);
        thread::sleep(KEY_EVENT_DELAY);
        self.enigo.key_up(modifier);
        Ok(())
    }
}

fn paste_shortcut() -> (Key, Key) {
    #[cfg(target_os = "macos")]
    {
        return (Key::Meta, Key::Raw(9));
    }

    #[cfg(target_os = "windows")]
    {
        return (Key::Control, Key::V);
    }

    #[cfg(all(not(target_os = "macos"), not(target_os = "windows")))]
    {
        (Key::Control, Key::Layout('v'))
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

        paste_with_ports(
            "new",
            true,
            &mut clipboard,
            &mut keyboard,
            PastePermissions {
                can_post_keyboard_events: true,
            },
        )
        .unwrap();

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

        paste_with_ports(
            "new",
            false,
            &mut clipboard,
            &mut keyboard,
            PastePermissions {
                can_post_keyboard_events: true,
            },
        )
        .unwrap();

        assert!(keyboard.pasted);
        assert_eq!(clipboard.text.as_deref(), Some("new"));
        assert_eq!(clipboard.writes, vec!["new"]);
    }

    #[cfg(target_os = "macos")]
    #[test]
    fn macos_paste_shortcut_uses_layout_independent_v_keycode() {
        assert_eq!(paste_shortcut(), (Key::Meta, Key::Raw(9)));
    }

    #[test]
    fn paste_fails_before_changing_clipboard_when_accessibility_is_missing() {
        let mut clipboard = FakeClipboard {
            text: Some("old".to_string()),
            writes: Vec::new(),
        };
        let mut keyboard = FakeKeyboard::default();

        let error = paste_with_ports(
            "new",
            false,
            &mut clipboard,
            &mut keyboard,
            PastePermissions {
                can_post_keyboard_events: false,
            },
        )
        .unwrap_err();

        assert!(error.to_string().contains("Accessibility"));
        assert!(!keyboard.pasted);
        assert_eq!(clipboard.text.as_deref(), Some("old"));
        assert!(clipboard.writes.is_empty());
    }
}
