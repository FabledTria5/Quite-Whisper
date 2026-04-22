package fabled.quitewhisper.domain

data class AppSettings(
    val hotkey: String,
    val modelPath: String?,
    val microphoneDeviceId: String?,
    val glossaryTerms: List<String>,
    val restoreClipboard: Boolean,
)

data class ModelStatus(
    val configuredPath: String?,
    val defaultModelPath: String,
    val defaultModelExists: Boolean,
    val configuredModelExists: Boolean,
)

data class MicrophoneStatus(
    val defaultDevice: String?,
    val devices: List<String>,
)

data class OverlayStatus(
    val state: String,
    val message: String,
)

sealed interface EngineEvent {
    data object EngineReady : EngineEvent
    data object RecordingStarted : EngineEvent
    data object RecordingStopped : EngineEvent
    data object TranscriptionStarted : EngineEvent
    data object TranscriptionDone : EngineEvent
    data object TranscriptionFailed : EngineEvent
    data class OverlayChanged(val overlay: OverlayStatus) : EngineEvent
}

sealed interface HotkeyEvent {
    data object Pressed : HotkeyEvent
    data object Released : HotkeyEvent
    data class Error(val message: String) : HotkeyEvent
}
