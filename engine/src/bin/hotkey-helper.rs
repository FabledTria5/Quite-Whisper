use global_hotkey::{
    hotkey::HotKey,
    GlobalHotKeyEvent, GlobalHotKeyManager, HotKeyState,
};
use serde_json::json;
use std::{env, str::FromStr};
use winit::{
    application::ApplicationHandler,
    event::WindowEvent,
    event_loop::{ActiveEventLoop, ControlFlow, EventLoop, EventLoopProxy},
    window::WindowId,
};
#[cfg(target_os = "macos")]
use winit::platform::macos::{ActivationPolicy, EventLoopBuilderExtMacOS};

fn main() -> anyhow::Result<()> {
    let hotkey_text = env::args()
        .nth(1)
        .unwrap_or_else(|| "Control+Alt+Space".to_string());
    let hotkey = match HotKey::from_str(&hotkey_text) {
        Ok(hotkey) => hotkey,
        Err(error) => {
            print_error(format!("Invalid hotkey `{hotkey_text}`: {error}"));
            anyhow::bail!("Invalid hotkey `{hotkey_text}`: {error}");
        }
    };

    let event_loop = build_event_loop()?;
    event_loop.set_control_flow(ControlFlow::Wait);

    let manager = GlobalHotKeyManager::new()?;
    if let Err(error) = manager.register(hotkey) {
        print_error(format!("Failed to register hotkey `{hotkey_text}`: {error}"));
        return Err(error.into());
    }
    let hotkey_id = hotkey.id();
    let proxy = event_loop.create_proxy();
    start_hotkey_event_forwarder(proxy);

    let mut app = HotkeyHelperApp {
        hotkey_id,
        _manager: manager,
    };
    event_loop.run_app(&mut app)?;
    Ok(())
}

fn build_event_loop() -> anyhow::Result<EventLoop<HotkeyHelperEvent>> {
    let mut builder = EventLoop::<HotkeyHelperEvent>::with_user_event();
    configure_event_loop(&mut builder);
    Ok(builder.build()?)
}

#[cfg(target_os = "macos")]
fn configure_event_loop(builder: &mut winit::event_loop::EventLoopBuilder<HotkeyHelperEvent>) {
    builder
        .with_activation_policy(ActivationPolicy::Accessory)
        .with_default_menu(false)
        .with_activate_ignoring_other_apps(false);
}

#[cfg(not(target_os = "macos"))]
fn configure_event_loop(_builder: &mut winit::event_loop::EventLoopBuilder<HotkeyHelperEvent>) {}

enum HotkeyHelperEvent {
    Hotkey(GlobalHotKeyEvent),
}

fn start_hotkey_event_forwarder(proxy: EventLoopProxy<HotkeyHelperEvent>) {
    std::thread::spawn(move || {
        while let Ok(event) = GlobalHotKeyEvent::receiver().recv() {
            if proxy.send_event(HotkeyHelperEvent::Hotkey(event)).is_err() {
                break;
            }
        }
    });
}

struct HotkeyHelperApp {
    hotkey_id: u32,
    _manager: GlobalHotKeyManager,
}

impl ApplicationHandler<HotkeyHelperEvent> for HotkeyHelperApp {
    fn resumed(&mut self, _event_loop: &ActiveEventLoop) {}

    fn user_event(&mut self, _event_loop: &ActiveEventLoop, event: HotkeyHelperEvent) {
        let HotkeyHelperEvent::Hotkey(event) = event;
        if let Some(message) = format_hotkey_event(self.hotkey_id, event) {
            println!("{message}");
        }
    }

    fn window_event(
        &mut self,
        _event_loop: &ActiveEventLoop,
        _window_id: WindowId,
        _event: WindowEvent,
    ) {
    }
}

fn print_error(message: String) {
    println!("{}", json!({ "type": "error", "message": message }));
}

fn format_hotkey_event(hotkey_id: u32, event: GlobalHotKeyEvent) -> Option<String> {
    if event.id != hotkey_id {
        return None;
    }

    let state = match event.state {
        HotKeyState::Pressed => "pressed",
        HotKeyState::Released => "released",
    };
    Some(json!({ "type": "hotkey", "state": state }).to_string())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn formats_only_matching_hotkey_events() {
        let event = GlobalHotKeyEvent {
            id: 7,
            state: HotKeyState::Pressed,
        };

        assert_eq!(
            format_hotkey_event(7, event),
            Some(r#"{"state":"pressed","type":"hotkey"}"#.to_string()),
        );
        assert_eq!(format_hotkey_event(8, event), None);
    }
}
