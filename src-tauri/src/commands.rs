use crate::{
    audio::{self, MicrophoneStatus},
    inserter::TextInserter,
    model::{self, ModelStatus},
    overlay::{self, OverlayState},
    settings::AppSettings,
    state::AppState,
};
use std::time::Duration;
use tauri::{AppHandle, Emitter, State};

#[tauri::command]
pub fn get_settings(state: State<'_, AppState>) -> Result<AppSettings, String> {
    Ok(state.settings.lock().clone())
}

#[tauri::command]
pub fn save_settings(
    state: State<'_, AppState>,
    settings: AppSettings,
) -> Result<AppSettings, String> {
    state
        .settings_store
        .save(&settings)
        .map_err(command_error)?;
    *state.settings.lock() = settings.clone();
    Ok(settings)
}

#[tauri::command]
pub fn get_model_status(state: State<'_, AppState>) -> Result<ModelStatus, String> {
    Ok(model::status(&state))
}

#[tauri::command]
pub async fn download_default_model(state: State<'_, AppState>) -> Result<ModelStatus, String> {
    let settings_store = state.settings_store.clone();
    let settings = state.settings.lock().clone();
    model::download_default(&settings_store)
        .await
        .map_err(command_error)?;
    Ok(model::status_for(&settings_store, settings))
}

#[tauri::command]
pub fn select_model_path() -> Result<Option<String>, String> {
    Ok(rfd::FileDialog::new()
        .add_filter("Whisper GGML model", &["bin"])
        .pick_file()
        .map(|path| path.to_string_lossy().to_string()))
}

#[tauri::command]
pub fn test_microphone() -> Result<MicrophoneStatus, String> {
    audio::microphone_status().map_err(command_error)
}

#[tauri::command]
pub fn start_recording(app: AppHandle, state: State<'_, AppState>) -> Result<(), String> {
    let microphone_device_id = state.settings.lock().microphone_device_id.clone();
    state
        .recorder
        .lock()
        .start(microphone_device_id.as_deref())
        .map_err(|error| fail_recording(&app, error))?;

    app.emit("recording_started", ()).map_err(command_error)?;
    overlay::set_status(&app, OverlayState::Listening, "Listening").map_err(command_error)?;
    Ok(())
}

#[tauri::command]
pub fn stop_recording_and_transcribe(
    app: AppHandle,
    state: State<'_, AppState>,
) -> Result<String, String> {
    let result = transcribe_and_paste(&app, &state);

    match result {
        Ok(text) => {
            app.emit("transcription_done", text.clone())
                .map_err(command_error)?;
            overlay::set_status(&app, OverlayState::Pasted, "Pasted").map_err(command_error)?;
            overlay::hide_after(app, Duration::from_millis(900));
            Ok(text)
        }
        Err(error) => {
            let message = error.to_string();
            let _ = app.emit("transcription_failed", message.clone());
            let _ = overlay::set_status(&app, OverlayState::Error, &message);
            overlay::hide_after(app, Duration::from_millis(1800));
            Err(message)
        }
    }
}

fn transcribe_and_paste(app: &AppHandle, state: &State<'_, AppState>) -> anyhow::Result<String> {
    let captured = state.recorder.lock().stop()?;
    app.emit("recording_stopped", ())?;
    overlay::set_status(app, OverlayState::Transcribing, "Transcribing")?;

    let samples = captured.normalized_16khz();
    if samples.is_empty() {
        anyhow::bail!("Recording contains only silence");
    }

    let settings = state.settings.lock().clone();
    let model_path = model::selected_model_path(&state.settings_store, &settings);
    let text = state
        .speech
        .lock()
        .transcribe(&model_path, &samples, &settings)?;

    if text.trim().is_empty() {
        anyhow::bail!("Whisper returned an empty transcription");
    }

    TextInserter::default().paste_text(&text, settings.restore_clipboard)?;
    Ok(text)
}

fn fail_recording(app: &AppHandle, error: anyhow::Error) -> String {
    let message = error.to_string();
    let _ = app.emit("transcription_failed", message.clone());
    let _ = overlay::set_status(app, OverlayState::Error, &message);
    overlay::hide_after(app.clone(), Duration::from_millis(1800));
    message
}

fn command_error(error: impl std::fmt::Display) -> String {
    error.to_string()
}
