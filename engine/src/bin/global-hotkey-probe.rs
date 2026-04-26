use global_hotkey::{
    hotkey::{Code, HotKey, Modifiers},
    GlobalHotKeyEvent, GlobalHotKeyManager, HotKeyState,
};
use std::env;
use winit::{
    application::ApplicationHandler,
    event::WindowEvent,
    event_loop::{ActiveEventLoop, ControlFlow, EventLoop},
    window::WindowId,
};

fn main() -> anyhow::Result<()> {
    let hotkey_text = env::args()
        .nth(1)
        .unwrap_or_else(|| "Control+Option+Space".to_string());
    let hotkey = parse_probe_hotkey(&hotkey_text)?;
    let manager = GlobalHotKeyManager::new()?;
    manager.register(hotkey)?;
    let hotkey_id = hotkey.id();

    eprintln!("Registered global hotkey: {hotkey_text}");
    eprintln!("Hold and release the shortcut. Press Ctrl+C to stop.");

    let event_loop = EventLoop::new()?;
    event_loop.set_control_flow(ControlFlow::Poll);
    let mut app = ProbeApp { hotkey_id, _manager: manager };
    event_loop.run_app(&mut app)?;
    Ok(())
}

struct ProbeApp {
    hotkey_id: u32,
    _manager: GlobalHotKeyManager,
}

impl ApplicationHandler for ProbeApp {
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
            if event.id == self.hotkey_id {
                match event.state {
                    HotKeyState::Pressed => println!("event id={} state=Pressed", event.id),
                    HotKeyState::Released => println!("event id={} state=Released", event.id),
                }
            } else {
                println!("event id={} state={:?}", event.id, event.state);
            }
        }
    }
}

fn parse_probe_hotkey(value: &str) -> anyhow::Result<HotKey> {
    let mut modifiers = Modifiers::empty();
    let mut code = None;

    for part in value.split('+').map(str::trim).filter(|part| !part.is_empty()) {
        match part.to_ascii_lowercase().as_str() {
            "control" | "ctrl" => modifiers |= Modifiers::CONTROL,
            "alt" | "option" => modifiers |= Modifiers::ALT,
            "shift" => modifiers |= Modifiers::SHIFT,
            "meta" | "command" | "cmd" | "win" | "super" => modifiers |= Modifiers::META,
            "space" => code = Some(Code::Space),
            "d" => code = Some(Code::KeyD),
            other => anyhow::bail!("Unsupported probe hotkey key: {other}"),
        }
    }

    let Some(code) = code else {
        anyhow::bail!("Probe hotkey must include a non-modifier key");
    };

    let modifiers = if modifiers.is_empty() {
        None
    } else {
        Some(modifiers)
    };
    Ok(HotKey::new(modifiers, code))
}
