use global_hotkey::{
    hotkey::HotKey,
    GlobalHotKeyEvent, GlobalHotKeyManager, HotKeyState,
};
use serde_json::json;
use std::{env, str::FromStr};
use winit::{
    application::ApplicationHandler,
    event::WindowEvent,
    event_loop::{ActiveEventLoop, ControlFlow, EventLoop},
    window::WindowId,
};

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

    let manager = GlobalHotKeyManager::new()?;
    if let Err(error) = manager.register(hotkey) {
        print_error(format!("Failed to register hotkey `{hotkey_text}`: {error}"));
        return Err(error.into());
    }
    let hotkey_id = hotkey.id();

    let event_loop = EventLoop::new()?;
    event_loop.set_control_flow(ControlFlow::Poll);
    let mut app = HotkeyHelperApp {
        hotkey_id,
        _manager: manager,
    };
    event_loop.run_app(&mut app)?;
    Ok(())
}

struct HotkeyHelperApp {
    hotkey_id: u32,
    _manager: GlobalHotKeyManager,
}

impl ApplicationHandler for HotkeyHelperApp {
    fn resumed(&mut self, _event_loop: &ActiveEventLoop) {}

    fn window_event(
        &mut self,
        _event_loop: &ActiveEventLoop,
        _window_id: WindowId,
        _event: WindowEvent,
    ) {
    }

    fn about_to_wait(&mut self, _event_loop: &ActiveEventLoop) {
        while let Ok(event) = GlobalHotKeyEvent::receiver().try_recv() {
            if event.id != self.hotkey_id {
                continue;
            }

            let state = match event.state {
                HotKeyState::Pressed => "pressed",
                HotKeyState::Released => "released",
            };
            println!("{}", json!({ "type": "hotkey", "state": state }));
        }
    }
}

fn print_error(message: String) {
    println!("{}", json!({ "type": "error", "message": message }));
}
