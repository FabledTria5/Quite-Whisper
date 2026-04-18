package fabled.quitewhisper.compose

import fabled.quitewhisper.compose.engine.AppSettings
import fabled.quitewhisper.compose.engine.MicrophoneStatus
import fabled.quitewhisper.compose.engine.ModelStatus
import fabled.quitewhisper.compose.engine.OverlayPayload

data class AppUiState(
    val status: String = "Starting...",
    val engineStatus: String = "Disconnected",
    val settings: AppSettings? = null,
    val modelStatus: ModelStatus? = null,
    val microphoneStatus: MicrophoneStatus? = null,
    val hotkeyDraft: String = "Control+Alt+Space",
    val modelPathDraft: String = "",
    val glossaryDraft: String = "",
    val restoreClipboardDraft: Boolean = true,
    val recording: Boolean = false,
    val overlay: OverlayPayload? = null,
    val mainWindowVisible: Boolean = true,
    val eventLog: List<String> = emptyList(),
)

sealed interface AppAction {
    data object Refresh : AppAction
    data object SaveSettings : AppAction
    data object DownloadDefaultModel : AppAction
    data object TestMicrophone : AppAction
    data object StartRecording : AppAction
    data object StopRecordingAndTranscribe : AppAction
    data object ShowMainWindow : AppAction
    data object HideMainWindow : AppAction
    data class HotkeyChanged(val value: String) : AppAction
    data class ModelPathChanged(val value: String) : AppAction
    data class GlossaryChanged(val value: String) : AppAction
    data class RestoreClipboardChanged(val value: Boolean) : AppAction
}
