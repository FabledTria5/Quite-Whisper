use crate::{
    audio::AudioRecorder,
    settings::{AppSettings, SettingsStore},
    speech::SpeechEngine,
};
use parking_lot::Mutex;

pub struct AppState {
    pub recorder: Mutex<AudioRecorder>,
    pub settings_store: SettingsStore,
    pub settings: Mutex<AppSettings>,
    pub speech: Mutex<SpeechEngine>,
}

impl AppState {
    pub fn new() -> anyhow::Result<Self> {
        let settings_store = SettingsStore::new()?;
        let settings = settings_store.load()?;

        Ok(Self {
            recorder: Mutex::new(AudioRecorder::default()),
            settings_store,
            settings: Mutex::new(settings),
            speech: Mutex::new(SpeechEngine::default()),
        })
    }
}
