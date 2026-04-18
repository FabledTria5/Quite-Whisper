use crate::{
    audio::{self, AudioRecorder},
    hotkey::{self, HotkeyEdge},
    inserter::TextInserter,
    model,
    overlay::{OverlayPayload, OverlayState},
    settings::{AppSettings, SettingsStore},
    speech::SpeechEngine,
};
use parking_lot::Mutex;
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use std::{
    io::{self, BufRead, Write},
    sync::{mpsc, Arc},
};

#[derive(Debug, Clone, Deserialize)]
pub struct EngineRequest {
    pub id: String,
    #[serde(flatten)]
    pub command: EngineCommand,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(tag = "command", rename_all = "camelCase")]
pub enum EngineCommand {
    GetSettings,
    SaveSettings { settings: AppSettings },
    GetModelStatus,
    DownloadDefaultModel,
    SelectModelPath,
    TestMicrophone,
    StartRecording,
    StopRecordingAndTranscribe,
    Shutdown,
}

#[derive(Debug, Clone, Serialize)]
#[serde(tag = "type", rename_all = "lowercase")]
pub enum EngineOutboundMessage {
    Result(EngineResult),
    Event(EngineEvent),
}

#[derive(Debug, Clone, Serialize)]
pub struct EngineResult {
    pub id: String,
    pub ok: bool,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub payload: Option<Value>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub error: Option<EngineErrorPayload>,
}

impl EngineResult {
    pub fn ok(id: impl Into<String>, payload: Value) -> Self {
        Self {
            id: id.into(),
            ok: true,
            payload: Some(payload),
            error: None,
        }
    }

    pub fn empty_ok(id: impl Into<String>) -> Self {
        Self::ok(id, json!({}))
    }

    pub fn err(id: impl Into<String>, error: EngineErrorPayload) -> Self {
        Self {
            id: id.into(),
            ok: false,
            payload: None,
            error: Some(error),
        }
    }
}

#[derive(Debug, Clone, Serialize, PartialEq, Eq)]
pub struct EngineErrorPayload {
    pub code: String,
    pub message: String,
}

impl EngineErrorPayload {
    pub fn new(code: impl Into<String>, message: impl Into<String>) -> Self {
        Self {
            code: code.into(),
            message: message.into(),
        }
    }
}

#[derive(Debug, Clone, Serialize)]
pub struct EngineEvent {
    pub event: EngineEventName,
    pub payload: Value,
}

impl EngineEvent {
    pub fn engine_ready() -> Self {
        Self {
            event: EngineEventName::EngineReady,
            payload: json!({}),
        }
    }

    fn recording_started() -> Self {
        Self {
            event: EngineEventName::RecordingStarted,
            payload: json!({}),
        }
    }

    fn recording_stopped() -> Self {
        Self {
            event: EngineEventName::RecordingStopped,
            payload: json!({}),
        }
    }

    fn transcription_started() -> Self {
        Self {
            event: EngineEventName::TranscriptionStarted,
            payload: json!({}),
        }
    }

    fn transcription_done(text: &str) -> Self {
        Self {
            event: EngineEventName::TranscriptionDone,
            payload: json!({ "text": text }),
        }
    }

    fn transcription_failed(error: &EngineErrorPayload) -> Self {
        Self {
            event: EngineEventName::TranscriptionFailed,
            payload: json!({ "error": error }),
        }
    }

    fn overlay_status_changed(state: OverlayState, message: impl Into<String>) -> Self {
        let payload = OverlayPayload {
            state,
            message: message.into(),
        };

        Self {
            event: EngineEventName::OverlayStatusChanged,
            payload: serde_json::to_value(payload).unwrap_or_else(|_| json!({})),
        }
    }
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub enum EngineEventName {
    EngineReady,
    RecordingStarted,
    RecordingStopped,
    TranscriptionStarted,
    TranscriptionDone,
    TranscriptionFailed,
    OverlayStatusChanged,
}

pub struct EngineOutcome {
    pub result: EngineResult,
    pub events: Vec<EngineEvent>,
    pub shutdown: bool,
}

pub struct EngineRuntime {
    recorder: Mutex<AudioRecorder>,
    settings_store: SettingsStore,
    settings: Mutex<AppSettings>,
    speech: Mutex<SpeechEngine>,
}

enum EngineLoopMessage {
    Request(EngineRequest),
    Outbound(EngineOutboundMessage),
}

impl EngineRuntime {
    pub fn new() -> anyhow::Result<Self> {
        let settings_store = SettingsStore::new()?;
        Self::from_store(settings_store)
    }

    pub fn from_store(settings_store: SettingsStore) -> anyhow::Result<Self> {
        let settings = settings_store.load()?;
        Ok(Self {
            recorder: Mutex::new(AudioRecorder::default()),
            settings_store,
            settings: Mutex::new(settings),
            speech: Mutex::new(SpeechEngine::default()),
        })
    }

    pub async fn handle(&self, request: EngineRequest) -> EngineOutcome {
        let id = request.id;
        let command_result = match request.command {
            EngineCommand::GetSettings => self.get_settings(&id),
            EngineCommand::SaveSettings { settings } => self.save_settings(&id, settings),
            EngineCommand::GetModelStatus => self.get_model_status(&id),
            EngineCommand::DownloadDefaultModel => self.download_default_model(&id).await,
            EngineCommand::SelectModelPath => EngineOutcome {
                result: EngineResult::ok(id, Value::Null),
                events: Vec::new(),
                shutdown: false,
            },
            EngineCommand::TestMicrophone => self.test_microphone(&id),
            EngineCommand::StartRecording => self.start_recording(&id),
            EngineCommand::StopRecordingAndTranscribe => self.stop_recording_and_transcribe(&id),
            EngineCommand::Shutdown => EngineOutcome {
                result: EngineResult::empty_ok(id),
                events: Vec::new(),
                shutdown: true,
            },
        };

        command_result
    }

    fn get_settings(&self, id: &str) -> EngineOutcome {
        result_only(EngineResult::ok(
            id,
            serde_json::to_value(self.settings.lock().clone()).unwrap_or_else(|_| json!({})),
        ))
    }

    fn save_settings(&self, id: &str, settings: AppSettings) -> EngineOutcome {
        match self.settings_store.save(&settings) {
            Ok(()) => {
                *self.settings.lock() = settings.clone();
                result_only(EngineResult::ok(
                    id,
                    serde_json::to_value(settings).unwrap_or_else(|_| json!({})),
                ))
            }
            Err(error) => result_only(EngineResult::err(id, classify_error("settings_error", error))),
        }
    }

    fn get_model_status(&self, id: &str) -> EngineOutcome {
        let settings = self.settings.lock().clone();
        result_only(EngineResult::ok(
            id,
            serde_json::to_value(model::status_for(&self.settings_store, settings))
                .unwrap_or_else(|_| json!({})),
        ))
    }

    async fn download_default_model(&self, id: &str) -> EngineOutcome {
        match model::download_default(&self.settings_store).await {
            Ok(_) => self.get_model_status(id),
            Err(error) => result_only(EngineResult::err(id, classify_error("model_error", error))),
        }
    }

    fn test_microphone(&self, id: &str) -> EngineOutcome {
        match audio::microphone_status() {
            Ok(status) => result_only(EngineResult::ok(
                id,
                serde_json::to_value(status).unwrap_or_else(|_| json!({})),
            )),
            Err(error) => {
                result_only(EngineResult::err(id, classify_error("microphone_error", error)))
            }
        }
    }

    fn start_recording(&self, id: &str) -> EngineOutcome {
        let microphone_device_id = self.settings.lock().microphone_device_id.clone();
        match self
            .recorder
            .lock()
            .start(microphone_device_id.as_deref())
        {
            Ok(()) => EngineOutcome {
                result: EngineResult::empty_ok(id),
                events: vec![
                    EngineEvent::recording_started(),
                    EngineEvent::overlay_status_changed(OverlayState::Listening, "Listening"),
                ],
                shutdown: false,
            },
            Err(error) => {
                let error = classify_error("recording_error", error);
                EngineOutcome {
                    result: EngineResult::err(id, error.clone()),
                    events: vec![
                        EngineEvent::transcription_failed(&error),
                        EngineEvent::overlay_status_changed(OverlayState::Error, &error.message),
                    ],
                    shutdown: false,
                }
            }
        }
    }

    fn stop_recording_and_transcribe(&self, id: &str) -> EngineOutcome {
        let mut events = vec![
            EngineEvent::recording_stopped(),
            EngineEvent::transcription_started(),
            EngineEvent::overlay_status_changed(OverlayState::Transcribing, "Transcribing"),
        ];

        match self.transcribe_and_paste() {
            Ok(text) => {
                events.push(EngineEvent::transcription_done(&text));
                events.push(EngineEvent::overlay_status_changed(OverlayState::Pasted, "Pasted"));
                EngineOutcome {
                    result: EngineResult::ok(id, json!({ "text": text })),
                    events,
                    shutdown: false,
                }
            }
            Err(error) => {
                let error = classify_error("transcription_error", error);
                events.push(EngineEvent::transcription_failed(&error));
                events.push(EngineEvent::overlay_status_changed(OverlayState::Error, &error.message));
                EngineOutcome {
                    result: EngineResult::err(id, error),
                    events,
                    shutdown: false,
                }
            }
        }
    }

    fn transcribe_and_paste(&self) -> anyhow::Result<String> {
        let captured = self.recorder.lock().stop()?;
        let samples = captured.normalized_16khz();
        if samples.is_empty() {
            anyhow::bail!("Recording contains only silence");
        }

        let settings = self.settings.lock().clone();
        let model_path = model::selected_model_path(&self.settings_store, &settings);
        let text = self
            .speech
            .lock()
            .transcribe(&model_path, &samples, &settings)?;

        if text.trim().is_empty() {
            anyhow::bail!("Whisper returned an empty transcription");
        }

        TextInserter::default().paste_text(&text, settings.restore_clipboard)?;
        Ok(text)
    }
}

pub async fn run_stdio() -> anyhow::Result<()> {
    let runtime = Arc::new(EngineRuntime::new()?);
    let (sender, receiver) = mpsc::channel::<EngineLoopMessage>();
    let mut stdout = io::stdout().lock();

    write_message(&mut stdout, &EngineOutboundMessage::Event(EngineEvent::engine_ready()))?;
    spawn_stdin_reader(sender.clone());
    spawn_hotkey_listener(Arc::clone(&runtime), sender);

    while let Ok(message) = receiver.recv() {
        match message {
            EngineLoopMessage::Request(request) => {
                let outcome = runtime.handle(request).await;
                for event in outcome.events {
                    write_message(&mut stdout, &EngineOutboundMessage::Event(event))?;
                }
                write_message(&mut stdout, &EngineOutboundMessage::Result(outcome.result))?;

                if outcome.shutdown {
                    break;
                }
            }
            EngineLoopMessage::Outbound(outbound) => {
                write_message(&mut stdout, &outbound)?;
            }
        }
    }

    Ok(())
}

fn spawn_stdin_reader(sender: mpsc::Sender<EngineLoopMessage>) {
    std::thread::spawn(move || {
        let stdin = io::stdin();
        for line in stdin.lock().lines() {
            let Ok(line) = line else {
                break;
            };
            if line.trim().is_empty() {
                continue;
            }

            match serde_json::from_str::<EngineRequest>(&line) {
                Ok(request) => {
                    if sender.send(EngineLoopMessage::Request(request)).is_err() {
                        break;
                    }
                }
                Err(error) => {
                    let result = EngineResult::err(
                        "",
                        EngineErrorPayload::new("invalid_request", error.to_string()),
                    );
                    if sender
                        .send(EngineLoopMessage::Outbound(EngineOutboundMessage::Result(result)))
                        .is_err()
                    {
                        break;
                    }
                }
            }
        }
    });
}

fn spawn_hotkey_listener(
    runtime: Arc<EngineRuntime>,
    sender: mpsc::Sender<EngineLoopMessage>,
) {
    let hotkey_runtime = Arc::clone(&runtime);
    hotkey::spawn_polling_listener(
        move || hotkey_runtime.settings.lock().hotkey.clone(),
        move |edge| {
            let outcome = match edge {
                HotkeyEdge::Pressed => runtime.start_recording("hotkey-start"),
                HotkeyEdge::Released => runtime.stop_recording_and_transcribe("hotkey-stop"),
                HotkeyEdge::None => return,
            };

            for event in outcome.events {
                if sender
                    .send(EngineLoopMessage::Outbound(EngineOutboundMessage::Event(event)))
                    .is_err()
                {
                    return;
                }
            }
        },
    );
}

fn result_only(result: EngineResult) -> EngineOutcome {
    EngineOutcome {
        result,
        events: Vec::new(),
        shutdown: false,
    }
}

fn classify_error(code: &'static str, error: impl std::fmt::Display) -> EngineErrorPayload {
    EngineErrorPayload::new(code, error.to_string())
}

fn write_message(
    writer: &mut impl Write,
    message: &EngineOutboundMessage,
) -> anyhow::Result<()> {
    serde_json::to_writer(&mut *writer, message)?;
    writer.write_all(b"\n")?;
    writer.flush()?;
    Ok(())
}
