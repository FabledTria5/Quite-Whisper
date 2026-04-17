mod audio;
mod commands;
mod inserter;
mod model;
mod overlay;
mod prompt;
pub mod settings;
pub mod speech;
mod state;
mod tray;

use tauri::{Manager, WindowEvent};

pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_global_shortcut::Builder::new().build())
        .setup(|app| {
            let state = state::AppState::new()?;
            app.manage(state);
            let tray_icon = tray::create(app.handle())?;
            app.manage(tray_icon);
            overlay::configure_overlay(app.handle())?;
            overlay::position_overlay(app.handle())?;
            Ok(())
        })
        .on_window_event(|window, event| {
            if window.label() == "main" {
                if let WindowEvent::CloseRequested { api, .. } = event {
                    api.prevent_close();
                    let _ = window.hide();
                }
            }
        })
        .invoke_handler(tauri::generate_handler![
            commands::get_settings,
            commands::save_settings,
            commands::get_model_status,
            commands::download_default_model,
            commands::select_model_path,
            commands::test_microphone,
            commands::start_recording,
            commands::stop_recording_and_transcribe,
        ])
        .run(tauri::generate_context!())
        .expect("failed to run QuiteWhisper");
}
