package fabled.quitewhisper.app.presentation.main

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fabled.quitewhisper.app.domain.AppSettings
import fabled.quitewhisper.app.domain.DictationRepository
import fabled.quitewhisper.app.domain.EngineEvent
import fabled.quitewhisper.app.domain.HotkeyEvent
import fabled.quitewhisper.app.domain.OverlayStatus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Stable
class MainViewModel(
    private val repository: DictationRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(MainState())
    val state: StateFlow<MainState> = _state.asStateFlow()

    private val _events = Channel<MainEvent>()
    val events = _events.receiveAsFlow()
    private var hotkeyRecordingActive = false

    init {
        collectEngineEvents()
        collectHotkeyEvents()
    }

    fun start() {
        viewModelScope.launch {
            runCatching {
                repository.start()
                _state.update { it.copy(engineStatus = "Connected", status = "Engine connected.") }
                val settings = refresh()
                startHotkeyHelper(settings.hotkey)
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        engineStatus = "Disconnected",
                        status = error.message ?: "Engine failed to start.",
                    )
                }
            }
        }
    }

    fun close() {
        repository.close()
    }

    fun onAction(action: MainAction) {
        when (action) {
            MainAction.Refresh -> launchAction { refresh() }
            MainAction.SaveSettings -> launchAction { saveSettings() }
            MainAction.DownloadDefaultModel -> launchAction { downloadDefaultModel() }
            MainAction.TestMicrophone -> launchAction { testMicrophone() }
            MainAction.StartRecording -> launchAction { startRecording() }
            MainAction.StopRecordingAndTranscribe -> launchAction { stopRecordingAndTranscribe() }
            MainAction.ShowMainWindow -> _state.update { it.copy(mainWindowVisible = true) }
            MainAction.HideMainWindow -> _state.update { it.copy(mainWindowVisible = false) }
            is MainAction.HotkeyChanged -> _state.update { it.copy(hotkeyDraft = action.value) }
            is MainAction.ModelPathChanged -> _state.update { it.copy(modelPathDraft = action.value) }
            is MainAction.GlossaryChanged -> _state.update { it.copy(glossaryDraft = action.value) }
            is MainAction.RestoreClipboardChanged -> _state.update {
                it.copy(restoreClipboardDraft = action.value)
            }
        }
    }

    private fun launchAction(
        onFailure: () -> Unit = {},
        block: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            runCatching { block() }
                .onFailure { error ->
                    onFailure()
                    _state.update {
                        it.copy(
                            status = error.message ?: "Action failed.",
                            recording = false,
                        )
                    }
                }
        }
    }

    private fun collectEngineEvents() {
        viewModelScope.launch {
            repository.events.collect { event -> handleEvent(event) }
        }
    }

    private suspend fun refresh(): AppSettings {
        _state.update { it.copy(status = "Refreshing engine state...") }
        val settings = repository.getSettings()
        val modelStatus = repository.getModelStatus()

        _state.update {
            it.copy(
                status = "Ready.",
                settings = settings,
                modelStatus = modelStatus,
                hotkeyDraft = settings.hotkey,
                modelPathDraft = settings.modelPath.orEmpty(),
                glossaryDraft = settings.glossaryTerms.joinToString("\n"),
                restoreClipboardDraft = settings.restoreClipboard,
            )
        }
        return settings
    }

    private suspend fun saveSettings() {
        val current = _state.value
        val settings = AppSettings(
            hotkey = current.hotkeyDraft.ifBlank { "Control+Alt+Space" },
            modelPath = current.modelPathDraft.ifBlank { null },
            microphoneDeviceId = current.settings?.microphoneDeviceId,
            glossaryTerms = current.glossaryDraft
                .lineSequence()
                .map(String::trim)
                .filter(String::isNotEmpty)
                .toList(),
            restoreClipboard = current.restoreClipboardDraft,
        )

        repository.saveSettings(settings)
        _state.update { it.copy(status = "Settings saved.", settings = settings) }
        refresh()
        startHotkeyHelper(settings.hotkey)
    }

    private suspend fun downloadDefaultModel() {
        _state.update { it.copy(status = "Downloading default model...") }
        _state.update {
            it.copy(
                status = "Default model downloaded.",
                modelStatus = repository.downloadDefaultModel(),
            )
        }
    }

    private suspend fun testMicrophone() {
        _state.update {
            it.copy(
                status = "Microphone check complete.",
                microphoneStatus = repository.testMicrophone(),
            )
        }
    }

    private suspend fun startRecording() {
        repository.startRecording()
        _state.update {
            it.copy(
                status = "Listening...",
                recording = true,
            )
        }
    }

    private suspend fun stopRecordingAndTranscribe() {
        repository.stopRecordingAndTranscribe()
        _state.update {
            it.copy(
                status = "Text pasted.",
                recording = false,
            )
        }
    }

    private fun collectHotkeyEvents() {
        viewModelScope.launch {
            repository.hotkeyEvents.collect { event -> handleHotkeyEvent(event) }
        }
    }

    private fun handleHotkeyEvent(event: HotkeyEvent) {
        when (event) {
            HotkeyEvent.Pressed -> {
                if (hotkeyRecordingActive || _state.value.recording) return
                hotkeyRecordingActive = true
                launchAction(onFailure = { hotkeyRecordingActive = false }) { startRecording() }
            }
            HotkeyEvent.Released -> {
                if (!hotkeyRecordingActive) return
                hotkeyRecordingActive = false
                launchAction { stopRecordingAndTranscribe() }
            }
            is HotkeyEvent.Error -> {
                _state.update { it.copy(status = event.message) }
            }
        }
    }

    private suspend fun startHotkeyHelper(hotkey: String) {
        runCatching { repository.startHotkey(hotkey) }
            .onFailure { error ->
                _state.update {
                    it.copy(status = error.message ?: "Hotkey helper failed to start.")
                }
            }
    }

    private fun handleEvent(event: EngineEvent) {
        val label = when (event) {
            EngineEvent.EngineReady -> "Engine ready"
            EngineEvent.RecordingStarted -> "Recording started"
            EngineEvent.RecordingStopped -> "Recording stopped"
            EngineEvent.TranscriptionStarted -> "Transcription started"
            EngineEvent.TranscriptionDone -> "Transcription done"
            EngineEvent.TranscriptionFailed -> "Transcription failed"
            is EngineEvent.OverlayChanged -> "Overlay status changed"
        }
        if (event is EngineEvent.OverlayChanged) {
            updateOverlay(event.overlay)
        }
        _state.update { state ->
            state.copy(eventLog = (listOf(label) + state.eventLog).take(8))
        }
    }

    private fun updateOverlay(payload: OverlayStatus) {
        _state.update {
            it.copy(
                overlay = payload.takeUnless { payload.state == "idle" },
                status = payload.message.ifBlank { it.status },
                recording = payload.state == "listening" || (it.recording && payload.state == "transcribing"),
            )
        }

        val delayMillis = when (payload.state) {
            "pasted" -> 900L
            "error" -> 1_800L
            else -> return
        }
        viewModelScope.launch {
            delay(delayMillis)
            _state.update { state ->
                if (state.overlay == payload) state.copy(overlay = null) else state
            }
        }
    }
}
