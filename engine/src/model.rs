use crate::{
    settings::{AppSettings, SettingsStore},
};
use futures_util::StreamExt;
use serde::Serialize;
use std::path::{Path, PathBuf};
use tokio::{fs, io::AsyncWriteExt};

pub const DEFAULT_MODEL_FILE: &str = "ggml-small-q5_1.bin";
pub const DEFAULT_MODEL_URL: &str =
    "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin?download=true";

#[derive(Debug, Clone, Serialize)]
pub struct ModelStatus {
    pub configured_path: Option<String>,
    pub default_model_path: String,
    pub default_model_exists: bool,
    pub configured_model_exists: bool,
}

pub fn default_model_path(settings_store: &SettingsStore) -> PathBuf {
    settings_store.models_dir().join(DEFAULT_MODEL_FILE)
}

pub fn status_for(settings_store: &SettingsStore, settings: AppSettings) -> ModelStatus {
    let default_model_path = default_model_path(settings_store);
    let configured_model_exists = settings
        .model_path
        .as_deref()
        .map(Path::new)
        .is_some_and(Path::exists);

    ModelStatus {
        configured_path: settings.model_path,
        default_model_exists: default_model_path.exists(),
        default_model_path: default_model_path.to_string_lossy().to_string(),
        configured_model_exists,
    }
}

pub fn selected_model_path(settings_store: &SettingsStore, settings: &AppSettings) -> PathBuf {
    settings
        .model_path
        .as_ref()
        .map(PathBuf::from)
        .unwrap_or_else(|| default_model_path(settings_store))
}

pub async fn download_default(settings_store: &SettingsStore) -> anyhow::Result<PathBuf> {
    let path = default_model_path(settings_store);
    if path.exists() {
        return Ok(path);
    }

    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent).await?;
    }

    let temp_path = path.with_extension("bin.download");
    let response = reqwest::Client::new()
        .get(DEFAULT_MODEL_URL)
        .send()
        .await?
        .error_for_status()?;
    let mut stream = response.bytes_stream();
    let mut file = fs::File::create(&temp_path).await?;

    while let Some(chunk) = stream.next().await {
        file.write_all(&chunk?).await?;
    }

    file.flush().await?;
    drop(file);
    fs::rename(temp_path, &path).await?;
    Ok(path)
}
