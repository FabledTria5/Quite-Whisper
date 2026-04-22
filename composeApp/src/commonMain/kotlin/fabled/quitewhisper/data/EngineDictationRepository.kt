package fabled.quitewhisper.data

import fabled.quitewhisper.data.engine.EngineConnection
import fabled.quitewhisper.data.engine.EngineEventName
import fabled.quitewhisper.data.engine.EngineJson
import fabled.quitewhisper.data.engine.EngineMessage
import fabled.quitewhisper.data.engine.EngineMicrophoneStatus
import fabled.quitewhisper.data.engine.EngineModelStatus
import fabled.quitewhisper.data.engine.EngineOverlayPayload
import fabled.quitewhisper.data.engine.EngineRequest
import fabled.quitewhisper.data.engine.newCommandId
import fabled.quitewhisper.data.engine.payloadAs
import fabled.quitewhisper.data.engine.toDomain
import fabled.quitewhisper.data.engine.toEngine
import fabled.quitewhisper.domain.AppSettings
import fabled.quitewhisper.domain.DictationRepository
import fabled.quitewhisper.domain.EngineEvent
import fabled.quitewhisper.domain.MicrophoneStatus
import fabled.quitewhisper.domain.ModelStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.json.decodeFromJsonElement

class EngineDictationRepository(
    private val engineConnection: EngineConnection,
) : DictationRepository {
    override val events: Flow<EngineEvent> = engineConnection.messages.mapNotNull { message ->
        (message as? EngineMessage.Event)?.toDomainEvent()
    }

    override suspend fun start() {
        engineConnection.start()
    }

    override fun close() {
        engineConnection.close()
    }

    override suspend fun getSettings(): AppSettings =
        engineConnection.send(EngineRequest.GetSettings(newCommandId()))
            .requireOk()
            .payloadAs<fabled.quitewhisper.data.engine.EngineAppSettings>()
            .toDomain()

    override suspend fun saveSettings(settings: AppSettings) {
        engineConnection.send(EngineRequest.SaveSettings(newCommandId(), settings.toEngine()))
            .requireOk()
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

    private fun EngineMessage.Result.requireOk(): EngineMessage.Result {
        if (ok) return this
        error(error?.message ?: "Engine command failed.")
    }
}
