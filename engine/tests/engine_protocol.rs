use quite_whisper_engine::{
    engine::{
        immediate_events_for_command, EngineCommand, EngineErrorPayload, EngineEvent,
        EngineOutboundMessage, EngineRequest, EngineResult,
    },
    settings::AppSettings,
};
use serde_json::json;

#[test]
fn parses_get_settings_command_from_json_line() {
    let request: EngineRequest =
        serde_json::from_str(r#"{"id":"cmd-1","command":"getSettings"}"#).unwrap();

    assert_eq!(request.id, "cmd-1");
    assert!(matches!(request.command, EngineCommand::GetSettings));
}

#[test]
fn parses_save_settings_command_with_payload() {
    let request: EngineRequest = serde_json::from_value(json!({
        "id": "cmd-2",
        "command": "saveSettings",
        "settings": {
            "hotkey": "Control+Alt+Space",
            "model_path": "C:/models/ggml.bin",
            "microphone_device_id": null,
            "glossary_terms": ["Kotlin", "Rust"],
            "restore_clipboard": true
        }
    }))
    .unwrap();

    let EngineCommand::SaveSettings { settings } = request.command else {
        panic!("expected saveSettings command");
    };

    assert_eq!(settings.model_path.as_deref(), Some("C:/models/ggml.bin"));
    assert_eq!(settings.glossary_terms, vec!["Kotlin", "Rust"]);
}

#[test]
fn serializes_result_with_payload() {
    let message = EngineOutboundMessage::Result(EngineResult::ok(
        "cmd-3",
        serde_json::to_value(AppSettings::default()).unwrap(),
    ));

    let serialized = serde_json::to_value(message).unwrap();

    assert_eq!(
        serialized,
        json!({
            "type": "result",
            "id": "cmd-3",
            "ok": true,
            "payload": {
                "hotkey": "Control+Alt+Space",
                "model_path": null,
                "microphone_device_id": null,
                "glossary_terms": ["Kotlin", "Jetpack Compose", "Gradle", "Rust", "Whisper"],
                "restore_clipboard": true
            }
        })
    );
}

#[test]
fn serializes_typed_error_result() {
    let message = EngineOutboundMessage::Result(EngineResult::err(
        "cmd-4",
        EngineErrorPayload::new("microphone_error", "No input device"),
    ));

    let serialized = serde_json::to_value(message).unwrap();

    assert_eq!(
        serialized,
        json!({
            "type": "result",
            "id": "cmd-4",
            "ok": false,
            "error": {
                "code": "microphone_error",
                "message": "No input device"
            }
        })
    );
}

#[test]
fn serializes_engine_ready_event() {
    let message = EngineOutboundMessage::Event(EngineEvent::engine_ready());

    let serialized = serde_json::to_value(message).unwrap();

    assert_eq!(
        serialized,
        json!({
            "type": "event",
            "event": "engineReady",
            "payload": {}
        })
    );
}

#[test]
fn stop_recording_command_has_immediate_transcribing_events() {
    let events = immediate_events_for_command(&EngineCommand::StopRecordingAndTranscribe);
    let serialized: Vec<_> = events
        .into_iter()
        .map(|event| serde_json::to_value(EngineOutboundMessage::Event(event)).unwrap())
        .collect();

    assert_eq!(
        serialized,
        vec![
            json!({
                "type": "event",
                "event": "recordingStopped",
                "payload": {}
            }),
            json!({
                "type": "event",
                "event": "transcriptionStarted",
                "payload": {}
            }),
            json!({
                "type": "event",
                "event": "overlayStatusChanged",
                "payload": {
                    "state": "transcribing",
                    "message": "Transcribing"
                }
            }),
        ],
    );
}
