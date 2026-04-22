package fabled.quitewhisper.presentation.main

sealed interface MainAction {
    data object Refresh : MainAction
    data object SaveSettings : MainAction
    data object DownloadDefaultModel : MainAction
    data object TestMicrophone : MainAction
    data object StartRecording : MainAction
    data object StopRecordingAndTranscribe : MainAction
    data object ShowMainWindow : MainAction
    data object HideMainWindow : MainAction
    data class HotkeyChanged(val value: String) : MainAction
    data class ModelPathChanged(val value: String) : MainAction
    data class GlossaryChanged(val value: String) : MainAction
    data class RestoreClipboardChanged(val value: Boolean) : MainAction
}
