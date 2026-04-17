use serde::Serialize;
use std::{thread, time::Duration};
use tauri::{AppHandle, Emitter, LogicalPosition, Manager};

#[derive(Debug, Clone, Copy, Serialize)]
#[serde(rename_all = "lowercase")]
pub enum OverlayState {
    Idle,
    Listening,
    Transcribing,
    Pasted,
    Error,
}

#[derive(Debug, Clone, Serialize)]
pub struct OverlayPayload {
    pub state: OverlayState,
    pub message: String,
}

pub fn set_status(
    app: &AppHandle,
    state: OverlayState,
    message: impl Into<String>,
) -> anyhow::Result<()> {
    let payload = OverlayPayload {
        state,
        message: message.into(),
    };
    app.emit("overlay_status", payload)?;

    let Some(window) = app.get_webview_window("overlay") else {
        return Ok(());
    };

    match state {
        OverlayState::Idle => window.hide()?,
        _ => {
            position_overlay(app)?;
            window.show()?;
        }
    }

    Ok(())
}

pub fn hide_after(app: AppHandle, duration: Duration) {
    thread::spawn(move || {
        thread::sleep(duration);
        let _ = set_status(&app, OverlayState::Idle, "");
    });
}

pub fn position_overlay(app: &AppHandle) -> anyhow::Result<()> {
    let Some(window) = app.get_webview_window("overlay") else {
        return Ok(());
    };

    let monitor = window
        .current_monitor()?
        .or(window.primary_monitor()?)
        .ok_or_else(|| anyhow::anyhow!("No monitor is available"))?;
    let scale = monitor.scale_factor();
    let monitor_size = monitor.size().to_logical::<f64>(scale);
    let monitor_position = monitor.position().to_logical::<f64>(scale);
    let window_size = window.outer_size()?.to_logical::<f64>(scale);
    let x = monitor_position.x + (monitor_size.width - window_size.width) / 2.0;
    let y = monitor_position.y + monitor_size.height - window_size.height - 42.0;

    window.set_position(LogicalPosition::new(x, y))?;
    Ok(())
}
