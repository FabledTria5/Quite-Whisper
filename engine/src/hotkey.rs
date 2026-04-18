use std::{thread, time::Duration};

const POLL_INTERVAL: Duration = Duration::from_millis(25);

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Hotkey {
    pub control: bool,
    pub alt: bool,
    pub shift: bool,
    pub meta: bool,
    key: HotkeyKey,
}

impl Hotkey {
    pub fn key_name(&self) -> &'static str {
        match self.key {
            HotkeyKey::Space => "Space",
        }
    }

    pub fn is_pressed(&self) -> bool {
        platform::is_hotkey_pressed(self)
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum HotkeyKey {
    Space,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum HotkeyEdge {
    None,
    Pressed,
    Released,
}

#[derive(Debug, Default)]
pub struct HotkeyState {
    pressed: bool,
}

impl HotkeyState {
    pub fn update(&mut self, is_pressed: bool) -> HotkeyEdge {
        match (self.pressed, is_pressed) {
            (false, true) => {
                self.pressed = true;
                HotkeyEdge::Pressed
            }
            (true, false) => {
                self.pressed = false;
                HotkeyEdge::Released
            }
            _ => HotkeyEdge::None,
        }
    }
}

pub fn parse_hotkey(value: &str) -> anyhow::Result<Hotkey> {
    let mut control = false;
    let mut alt = false;
    let mut shift = false;
    let mut meta = false;
    let mut key = None;

    for part in value.split('+').map(str::trim).filter(|part| !part.is_empty()) {
        match part.to_ascii_lowercase().as_str() {
            "control" | "ctrl" => control = true,
            "alt" | "option" => alt = true,
            "shift" => shift = true,
            "meta" | "command" | "cmd" | "win" | "super" => meta = true,
            "space" => key = Some(HotkeyKey::Space),
            other => anyhow::bail!("Unsupported hotkey key: {other}"),
        }
    }

    let Some(key) = key else {
        anyhow::bail!("Hotkey must include a non-modifier key");
    };

    Ok(Hotkey {
        control,
        alt,
        shift,
        meta,
        key,
    })
}

pub fn spawn_polling_listener(
    hotkey_provider: impl Fn() -> String + Send + 'static,
    mut on_edge: impl FnMut(HotkeyEdge) + Send + 'static,
) {
    thread::spawn(move || {
        let mut state = HotkeyState::default();

        loop {
            let is_pressed = hotkey_provider()
                .parse::<String>()
                .ok()
                .and_then(|hotkey| parse_hotkey(&hotkey).ok())
                .is_some_and(|hotkey| hotkey.is_pressed());
            let edge = state.update(is_pressed);
            if edge != HotkeyEdge::None {
                on_edge(edge);
            }
            thread::sleep(POLL_INTERVAL);
        }
    });
}

#[cfg(target_os = "windows")]
mod platform {
    use super::{Hotkey, HotkeyKey};

    const VK_CONTROL: i32 = 0x11;
    const VK_MENU: i32 = 0x12;
    const VK_SHIFT: i32 = 0x10;
    const VK_LWIN: i32 = 0x5B;
    const VK_RWIN: i32 = 0x5C;
    const VK_SPACE: i32 = 0x20;
    const KEY_DOWN_MASK: i16 = i16::MIN;

    #[link(name = "user32")]
    extern "system" {
        fn GetAsyncKeyState(v_key: i32) -> i16;
    }

    pub fn is_hotkey_pressed(hotkey: &Hotkey) -> bool {
        modifier_matches(hotkey.control, VK_CONTROL)
            && modifier_matches(hotkey.alt, VK_MENU)
            && modifier_matches(hotkey.shift, VK_SHIFT)
            && meta_matches(hotkey.meta)
            && key_down(match hotkey.key {
                HotkeyKey::Space => VK_SPACE,
            })
    }

    fn modifier_matches(required: bool, key: i32) -> bool {
        !required || key_down(key)
    }

    fn meta_matches(required: bool) -> bool {
        !required || key_down(VK_LWIN) || key_down(VK_RWIN)
    }

    fn key_down(key: i32) -> bool {
        unsafe { GetAsyncKeyState(key) & KEY_DOWN_MASK != 0 }
    }
}

#[cfg(not(target_os = "windows"))]
mod platform {
    use super::Hotkey;

    pub fn is_hotkey_pressed(_hotkey: &Hotkey) -> bool {
        false
    }
}
