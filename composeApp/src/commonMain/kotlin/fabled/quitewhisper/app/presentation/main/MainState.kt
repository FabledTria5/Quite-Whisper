package fabled.quitewhisper.app.presentation.main

import androidx.compose.runtime.Immutable
import fabled.quitewhisper.app.domain.AppSettings
import fabled.quitewhisper.app.domain.MicrophoneStatus
import fabled.quitewhisper.app.domain.ModelStatus
import fabled.quitewhisper.app.domain.OverlayStatus

@Immutable
data class MainState(
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
    val overlay: OverlayStatus? = null,
    val mainWindowVisible: Boolean = true,
    val eventLog: List<String> = emptyList(),
)
