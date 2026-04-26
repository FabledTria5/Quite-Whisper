package fabled.quitewhisper.app.data

import fabled.quitewhisper.app.data.engine.EngineAppSettings
import fabled.quitewhisper.app.data.engine.EngineConnection
import fabled.quitewhisper.app.data.engine.EngineEventName
import fabled.quitewhisper.app.data.engine.EngineJson
import fabled.quitewhisper.app.data.engine.EngineMessage
import fabled.quitewhisper.app.data.engine.EngineMicrophoneStatus
import fabled.quitewhisper.app.data.engine.EngineModelStatus
import fabled.quitewhisper.app.data.engine.EngineOverlayPayload
import fabled.quitewhisper.app.data.engine.EngineRequest
import fabled.quitewhisper.app.data.engine.HotkeyConnection
import fabled.quitewhisper.app.data.engine.newCommandId
import fabled.quitewhisper.app.data.engine.payloadAs
import fabled.quitewhisper.app.data.engine.toDomain
import fabled.quitewhisper.app.data.engine.toEngine
import fabled.quitewhisper.app.domain.AppSettings
import fabled.quitewhisper.app.domain.DictationRepository
import fabled.quitewhisper.app.domain.EngineEvent
import fabled.quitewhisper.app.domain.HotkeyEvent
import fabled.quitewhisper.app.domain.MicrophoneStatus
import fabled.quitewhisper.app.domain.ModelStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.json.decodeFromJsonElement

class EngineDictationRepository(
    private val engineConnection: EngineConnection,
    private val hotkeyConnection: HotkeyConnection,
) : DictationRepository {
    override val events: Flow<EngineEvent> = engineConnection.messages.mapNotNull { message ->
        (message as? EngineMessage.Event)?.toDomainEvent()
    }

    override val hotkeyEvents: Flow<HotkeyEvent> = hotkeyConnection.events.mapNotNull { event ->
        event.toDomain()
    }

    override suspend fun start() {
        engineConnection.start()
    }

    override fun close() {
        hotkeyConnection.close()
        engineConnection.close()
    }

    override suspend fun getSettings(): AppSettings =
        engineConnection.send(EngineRequest.GetSettings(newCommandId()))
            .requireOk()
            .payloadAs<EngineAppSettings>()
            .toDomain()

    override suspend fun saveSettings(settings: AppSettings) {
        engineConnection.send(EngineRequest.SaveSettings(newCommandId(), settings.toEngine()))
            .requireOk()
    }

    override suspend fun startHotkey(hotkey: String) {
        hotkeyConnection.start(hotkey)
    }

    override suspend fun getModelStatus(): ModelStatus =
        engineConnection.send(EngineRequest.GetModelStatus(newCommandId()))
            .requireOk()
            .payloadAs<EngineModelStatus>()
            .toDomain()

    override suspend fun downloadDefaultModel(): ModelStatus =
        engineConnection.send(
            EngineRequest.DownloadDefaultModel(newCommandId()),
            timeoutMillis = 20 * 60 * 1000,
        )
            .requireOk()
            .payloadAs<EngineModelStatus>()
            .toDomain()

    override suspend fun testMicrophone(): MicrophoneStatus =
        engineConnection.send(EngineRequest.TestMicrophone(newCommandId()))
            .requireOk()
            .payloadAs<EngineMicrophoneStatus>()
            .toDomain()

    override suspend fun startRecording() {
        engineConnection.send(EngineRequest.StartRecording(newCommandId()))
            .requireOk()
    }

    override suspend fun stopRecordingAndTranscribe() {
        engineConnection.send(
            EngineRequest.StopRecordingAndTranscribe(newCommandId()),
            timeoutMillis = 5 * 60 * 1000,
        )
            .requireOk()
    }

    private fun EngineMessage.Event.toDomainEvent(): EngineEvent = when (event) {
        EngineEventName.EngineReady -> EngineEvent.EngineReady
        EngineEventName.RecordingStarted -> EngineEvent.RecordingStarted
        EngineEventName.RecordingStopped -> EngineEvent.RecordingStopped
        EngineEventName.TranscriptionStarted -> EngineEvent.TranscriptionStarted
        EngineEventName.TranscriptionDone -> EngineEvent.TranscriptionDone
        EngineEventName.TranscriptionFailed -> EngineEvent.TranscriptionFailed
        EngineEventName.OverlayStatusChanged -> EngineEvent.OverlayChanged(
            EngineJson.json.decodeFromJsonElement<EngineOverlayPayload>(payload).toDomain(),
        )
    }

    private fun fabled.quitewhisper.app.data.engine.HotkeyEvent.toDomain(): HotkeyEvent = when (this) {
        fabled.quitewhisper.app.data.engine.HotkeyEvent.Pressed -> HotkeyEvent.Pressed
        fabled.quitewhisper.app.data.engine.HotkeyEvent.Released -> HotkeyEvent.Released
        is fabled.quitewhisper.app.data.engine.HotkeyEvent.Error -> HotkeyEvent.Error(message)
    }

    private fun EngineMessage.Result.requireOk(): EngineMessage.Result {
        if (ok) return this
        error(error?.message ?: "Engine command failed.")
    }
}
