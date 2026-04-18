use quite_whisper_engine::hotkey::{parse_hotkey, HotkeyEdge, HotkeyState};

#[test]
fn parses_control_alt_space_hotkey() {
    let hotkey = parse_hotkey("Control+Alt+Space").unwrap();

    assert!(hotkey.control);
    assert!(hotkey.alt);
    assert!(!hotkey.shift);
    assert_eq!(hotkey.key_name(), "Space");
}

#[test]
fn hotkey_state_emits_press_and_release_edges_once() {
    let mut state = HotkeyState::default();

    assert_eq!(state.update(false), HotkeyEdge::None);
    assert_eq!(state.update(true), HotkeyEdge::Pressed);
    assert_eq!(state.update(true), HotkeyEdge::None);
    assert_eq!(state.update(false), HotkeyEdge::Released);
    assert_eq!(state.update(false), HotkeyEdge::None);
}
