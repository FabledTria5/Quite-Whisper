use serde::Serialize;

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
