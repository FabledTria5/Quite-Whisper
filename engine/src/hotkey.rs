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

#[cfg(target_os = "macos")]
mod platform {
    use super::{Hotkey, HotkeyKey};

    const HID_SYSTEM_STATE: u32 = 1;
    const KEY_SPACE: u16 = 49;
    const KEY_LEFT_COMMAND: u16 = 55;
    const KEY_RIGHT_COMMAND: u16 = 54;
    const KEY_LEFT_SHIFT: u16 = 56;
    const KEY_RIGHT_SHIFT: u16 = 60;
    const KEY_LEFT_OPTION: u16 = 58;
    const KEY_RIGHT_OPTION: u16 = 61;
    const KEY_LEFT_CONTROL: u16 = 59;
    const KEY_RIGHT_CONTROL: u16 = 62;

    #[link(name = "ApplicationServices", kind = "framework")]
    extern "C" {
        fn CGEventSourceKeyState(state_id: u32, key: u16) -> bool;
    }

    pub fn is_hotkey_pressed(hotkey: &Hotkey) -> bool {
        modifier_matches(hotkey.control, &[KEY_LEFT_CONTROL, KEY_RIGHT_CONTROL])
            && modifier_matches(hotkey.alt, &[KEY_LEFT_OPTION, KEY_RIGHT_OPTION])
            && modifier_matches(hotkey.shift, &[KEY_LEFT_SHIFT, KEY_RIGHT_SHIFT])
            && modifier_matches(hotkey.meta, &[KEY_LEFT_COMMAND, KEY_RIGHT_COMMAND])
            && key_down(primary_key_code(hotkey))
    }

    pub(super) fn primary_key_code(hotkey: &Hotkey) -> u16 {
        match hotkey.key {
            HotkeyKey::Space => KEY_SPACE,
        }
    }

    #[cfg(test)]
    pub(super) fn required_modifier_key_codes(hotkey: &Hotkey) -> Vec<Vec<u16>> {
        let mut key_codes = Vec::new();
        if hotkey.control {
            key_codes.push(vec![KEY_LEFT_CONTROL, KEY_RIGHT_CONTROL]);
        }
        if hotkey.alt {
            key_codes.push(vec![KEY_LEFT_OPTION, KEY_RIGHT_OPTION]);
        }
        if hotkey.shift {
            key_codes.push(vec![KEY_LEFT_SHIFT, KEY_RIGHT_SHIFT]);
        }
        if hotkey.meta {
            key_codes.push(vec![KEY_LEFT_COMMAND, KEY_RIGHT_COMMAND]);
        }
        key_codes
    }

    fn modifier_matches(required: bool, keys: &[u16]) -> bool {
        !required || keys.iter().any(|key| key_down(*key))
    }

    fn key_down(key: u16) -> bool {
        unsafe { CGEventSourceKeyState(HID_SYSTEM_STATE, key) }
    }
}

#[cfg(all(not(target_os = "windows"), not(target_os = "macos")))]
mod platform {
    use super::Hotkey;

    pub fn is_hotkey_pressed(_hotkey: &Hotkey) -> bool {
        false
    }
}

#[cfg(all(test, target_os = "macos"))]
mod macos_tests {
    use super::{parse_hotkey, platform};

    #[test]
    fn maps_supported_hotkey_parts_to_macos_virtual_key_codes() {
        let hotkey = parse_hotkey("Control+Option+Command+Shift+Space").unwrap();

        assert_eq!(platform::primary_key_code(&hotkey), 49);
        assert_eq!(
            platform::required_modifier_key_codes(&hotkey),
            vec![vec![59, 62], vec![58, 61], vec![56, 60], vec![55, 54]],
        );
    }
}
