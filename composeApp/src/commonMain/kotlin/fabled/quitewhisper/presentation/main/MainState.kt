package fabled.quitewhisper.presentation.main

import fabled.quitewhisper.domain.AppSettings
import fabled.quitewhisper.domain.MicrophoneStatus
import fabled.quitewhisper.domain.ModelStatus
import fabled.quitewhisper.domain.OverlayStatus

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
