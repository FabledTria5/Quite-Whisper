package fabled.quitewhisper.compose

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import fabled.quitewhisper.compose.engine.AppSettings
import fabled.quitewhisper.compose.engine.EngineClient
import fabled.quitewhisper.compose.engine.EngineConnection
import fabled.quitewhisper.compose.engine.EngineEventName
import fabled.quitewhisper.compose.engine.EngineJson
import fabled.quitewhisper.compose.engine.EngineMessage
import fabled.quitewhisper.compose.engine.EngineRequest
import fabled.quitewhisper.compose.engine.OverlayPayload
import fabled.quitewhisper.compose.engine.newCommandId
import fabled.quitewhisper.compose.engine.payloadAs
import kotlinx.serialization.json.decodeFromJsonElement

class AppController(
    private val scope: CoroutineScope,
    private val engineClient: EngineConnection = EngineClient(),
) {
    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state

    fun start() {
        scope.launch {
            runCatching {
                engineClient.start()
                _state.update { it.copy(engineStatus = "Connected", status = "Engine connected.") }
                collectEngineMessages()
                refresh()
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
        engineClient.close()
    }

    fun onAction(action: AppAction) {
        when (action) {
            AppAction.Refresh -> launchAction { refresh() }
            AppAction.SaveSettings -> launchAction { saveSettings() }
            AppAction.DownloadDefaultModel -> launchAction { downloadDefaultModel() }
            AppAction.TestMicrophone -> launchAction { testMicrophone() }
            AppAction.StartRecording -> launchAction { startRecording() }
            AppAction.StopRecordingAndTranscribe -> launchAction { stopRecordingAndTranscribe() }
            AppAction.ShowMainWindow -> _state.update { it.copy(mainWindowVisible = true) }
            AppAction.HideMainWindow -> _state.update { it.copy(mainWindowVisible = false) }
            is AppAction.HotkeyChanged -> _state.update { it.copy(hotkeyDraft = action.value) }
            is AppAction.ModelPathChanged -> _state.update { it.copy(modelPathDraft = action.value) }
            is AppAction.GlossaryChanged -> _state.update { it.copy(glossaryDraft = action.value) }
            is AppAction.RestoreClipboardChanged -> _state.update {
                it.copy(restoreClipboardDraft = action.value)
            }
        }
    }

    private fun launchAction(block: suspend () -> Unit) {
        scope.launch {
            runCatching { block() }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            status = error.message ?: "Action failed.",
                            recording = false,
                        )
                    }
                }
        }
    }

    private fun collectEngineMessages() {
        scope.launch {
            engineClient.messages.collect { message ->
                onEngineMessage(message)
            }
        }
    }

    internal fun onEngineMessage(message: EngineMessage) {
        if (message is EngineMessage.Event) {
            handleEvent(message)
        }
    }

    private suspend fun refresh() {
        _state.update { it.copy(status = "Refreshing engine state...") }
        val settings = engineClient
            .send(EngineRequest.GetSettings(newCommandId()))
            .payloadAs<AppSettings>()
        val modelStatus = engineClient
            .send(EngineRequest.GetModelStatus(newCommandId()))
            .payloadAs<fabled.quitewhisper.compose.engine.ModelStatus>()

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

        val result = engineClient.send(EngineRequest.SaveSettings(newCommandId(), settings))
        if (result.ok) {
            _state.update { it.copy(status = "Settings saved.", settings = settings) }
            refresh()
        } else {
            _state.update { it.copy(status = result.error?.message ?: "Settings save failed.") }
        }
    }

    private suspend fun downloadDefaultModel() {
        _state.update { it.copy(status = "Downloading default model...") }
        val result = engineClient.send(EngineRequest.DownloadDefaultModel(newCommandId()), timeoutMillis = 20 * 60 * 1000)
        if (result.ok) {
            _state.update {
                it.copy(
                    status = "Default model downloaded.",
                    modelStatus = result.payloadAs(),
                )
            }
        } else {
            _state.update { it.copy(status = result.error?.message ?: "Model download failed.") }
        }
    }

    private suspend fun testMicrophone() {
        val result = engineClient.send(EngineRequest.TestMicrophone(newCommandId()))
        if (result.ok) {
            _state.update {
                it.copy(
                    status = "Microphone check complete.",
                    microphoneStatus = result.payloadAs(),
                )
            }
        } else {
            _state.update { it.copy(status = result.error?.message ?: "Microphone check failed.") }
        }
    }

    private suspend fun startRecording() {
        val result = engineClient.send(EngineRequest.StartRecording(newCommandId()))
        _state.update {
            it.copy(
                status = result.error?.message ?: "Listening...",
                recording = result.ok,
            )
        }
    }

    private suspend fun stopRecordingAndTranscribe() {
        val result = engineClient.send(EngineRequest.StopRecordingAndTranscribe(newCommandId()), timeoutMillis = 5 * 60 * 1000)
        _state.update {
            it.copy(
                status = result.error?.message ?: "Text pasted.",
                recording = false,
            )
        }
    }

    private fun handleEvent(event: EngineMessage.Event) {
        val label = when (event.event) {
            EngineEventName.EngineReady -> "Engine ready"
            EngineEventName.RecordingStarted -> "Recording started"
            EngineEventName.RecordingStopped -> "Recording stopped"
            EngineEventName.TranscriptionStarted -> "Transcription started"
            EngineEventName.TranscriptionDone -> "Transcription done"
            EngineEventName.TranscriptionFailed -> "Transcription failed"
            EngineEventName.OverlayStatusChanged -> "Overlay status changed"
        }
        if (event.event == EngineEventName.OverlayStatusChanged) {
            updateOverlay(EngineJson.json.decodeFromJsonElement<OverlayPayload>(event.payload))
        }
        _state.update { state ->
            state.copy(eventLog = (listOf(label) + state.eventLog).take(8))
        }
    }

    private fun updateOverlay(payload: OverlayPayload) {
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
        scope.launch {
            delay(delayMillis)
            _state.update { state ->
                if (state.overlay == payload) state.copy(overlay = null) else state
            }
        }
    }
}
