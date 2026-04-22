use quite_whisper_engine::hotkey::{parse_hotkey, HotkeyEdge, HotkeyState};
use std::{env, thread, time::Duration};

const POLL_INTERVAL: Duration = Duration::from_millis(100);

fn main() -> anyhow::Result<()> {
    let hotkey = env::args()
        .nth(1)
        .unwrap_or_else(|| "Control+Option+Space".to_string());
    let parsed = parse_hotkey(&hotkey)?;
    let mut state = HotkeyState::default();

    eprintln!("Probing hotkey: {hotkey}");
    eprintln!("Hold the shortcut now. Press Ctrl+C to stop.");

    loop {
        let pressed = parsed.is_pressed();
        match state.update(pressed) {
            HotkeyEdge::Pressed => println!("EDGE pressed"),
            HotkeyEdge::Released => println!("EDGE released"),
            HotkeyEdge::None => {}
        }

        print_platform_snapshot(pressed);
        thread::sleep(POLL_INTERVAL);
    }
}

#[cfg(target_os = "macos")]
fn print_platform_snapshot(parsed_pressed: bool) {
    let snapshot = macos::snapshot();
    println!(
        "parsed={parsed_pressed} space={} ctrl_l={} ctrl_r={} opt_l={} opt_r={} shift_l={} shift_r={} cmd_l={} cmd_r={}",
        snapshot.space,
        snapshot.left_control,
        snapshot.right_control,
        snapshot.left_option,
        snapshot.right_option,
        snapshot.left_shift,
        snapshot.right_shift,
        snapshot.left_command,
        snapshot.right_command,
    );
}

#[cfg(not(target_os = "macos"))]
fn print_platform_snapshot(parsed_pressed: bool) {
    println!("parsed={parsed_pressed}");
}

#[cfg(target_os = "macos")]
mod macos {
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

    pub struct Snapshot {
        pub space: bool,
        pub left_control: bool,
        pub right_control: bool,
        pub left_option: bool,
        pub right_option: bool,
        pub left_shift: bool,
        pub right_shift: bool,
        pub left_command: bool,
        pub right_command: bool,
    }

    #[link(name = "ApplicationServices", kind = "framework")]
    extern "C" {
        fn CGEventSourceKeyState(state_id: u32, key: u16) -> bool;
    }

    pub fn snapshot() -> Snapshot {
        Snapshot {
            space: key_down(KEY_SPACE),
            left_control: key_down(KEY_LEFT_CONTROL),
            right_control: key_down(KEY_RIGHT_CONTROL),
            left_option: key_down(KEY_LEFT_OPTION),
            right_option: key_down(KEY_RIGHT_OPTION),
            left_shift: key_down(KEY_LEFT_SHIFT),
            right_shift: key_down(KEY_RIGHT_SHIFT),
            left_command: key_down(KEY_LEFT_COMMAND),
            right_command: key_down(KEY_RIGHT_COMMAND),
        }
    }

    fn key_down(key: u16) -> bool {
        unsafe { CGEventSourceKeyState(HID_SYSTEM_STATE, key) }
    }
}
