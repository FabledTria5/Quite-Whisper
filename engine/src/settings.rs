use serde::{Deserialize, Serialize};
use std::{
    fs,
    path::{Path, PathBuf},
};

const SETTINGS_FILE: &str = "settings.json";
pub const DEFAULT_HOTKEY: &str = "Control+Alt+Space";

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct AppSettings {
    pub hotkey: String,
    pub model_path: Option<String>,
    pub microphone_device_id: Option<String>,
    pub glossary_terms: Vec<String>,
    pub restore_clipboard: bool,
}

impl Default for AppSettings {
    fn default() -> Self {
        Self {
            hotkey: DEFAULT_HOTKEY.to_string(),
            model_path: None,
            microphone_device_id: None,
            glossary_terms: vec![
                "Kotlin".to_string(),
                "Jetpack Compose".to_string(),
                "Gradle".to_string(),
                "Rust".to_string(),
                "Whisper".to_string(),
            ],
            restore_clipboard: true,
        }
    }
}

#[derive(Debug, Clone)]
pub struct SettingsStore {
    app_dir: PathBuf,
}

impl SettingsStore {
    pub fn new() -> anyhow::Result<Self> {
        let app_dir = dirs::config_dir()
            .ok_or_else(|| anyhow::anyhow!("Could not resolve the user config directory"))?
            .join("QuiteWhisper");
        Ok(Self::from_dir(app_dir))
    }

    pub fn from_dir(app_dir: impl Into<PathBuf>) -> Self {
        Self {
            app_dir: app_dir.into(),
        }
    }

    pub fn app_dir(&self) -> &Path {
        &self.app_dir
    }

    pub fn models_dir(&self) -> PathBuf {
        self.app_dir.join("models")
    }

    pub fn load(&self) -> anyhow::Result<AppSettings> {
        let path = self.settings_path();
        if !path.exists() {
            return Ok(AppSettings::default());
        }

        let content = fs::read_to_string(path)?;
        let settings = serde_json::from_str::<AppSettings>(&content)?;
        Ok(settings)
    }

    pub fn save(&self, settings: &AppSettings) -> anyhow::Result<()> {
        fs::create_dir_all(&self.app_dir)?;
        let content = serde_json::to_string_pretty(settings)?;
        fs::write(self.settings_path(), content)?;
        Ok(())
    }

    fn settings_path(&self) -> PathBuf {
        self.app_dir.join(SETTINGS_FILE)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn load_returns_defaults_when_file_is_missing() {
        let dir = tempfile::tempdir().unwrap();
        let store = SettingsStore::from_dir(dir.path());

        assert_eq!(store.load().unwrap(), AppSettings::default());
    }

    #[test]
    fn save_and_load_roundtrips_settings() {
        let dir = tempfile::tempdir().unwrap();
        let store = SettingsStore::from_dir(dir.path());
        let settings = AppSettings {
            hotkey: "Ctrl+Shift+Space".to_string(),
            model_path: Some("C:/models/model.bin".to_string()),
            microphone_device_id: Some("default".to_string()),
            glossary_terms: vec!["Ktor".to_string(), "StateFlow".to_string()],
            restore_clipboard: false,
        };

        store.save(&settings).unwrap();

        assert_eq!(store.load().unwrap(), settings);
    }
}
