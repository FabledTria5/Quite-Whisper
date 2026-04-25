package fabled.quitewhisper.app.data.engine

import fabled.quitewhisper.app.domain.AppSettings
import fabled.quitewhisper.app.domain.MicrophoneStatus
import fabled.quitewhisper.app.domain.ModelStatus
import fabled.quitewhisper.app.domain.OverlayStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
data class EngineAppSettings(
    val hotkey: String,
    @SerialName("model_path")
    val modelPath: String?,
    @SerialName("microphone_device_id")
    val microphoneDeviceId: String?,
    @SerialName("glossary_terms")
    val glossaryTerms: List<String>,
    @SerialName("restore_clipboard")
    val restoreClipboard: Boolean,
)

@Serializable
data class EngineModelStatus(
    @SerialName("configured_path")
    val configuredPath: String?,
    @SerialName("default_model_path")
    val defaultModelPath: String,
    @SerialName("default_model_exists")
    val defaultModelExists: Boolean,
    @SerialName("configured_model_exists")
    val configuredModelExists: Boolean,
)

@Serializable
data class EngineMicrophoneStatus(
    @SerialName("default_device")
    val defaultDevice: String?,
    val devices: List<String>,
)

@Serializable
data class EngineOverlayPayload(
    val state: String,
    val message: String,
)

@Serializable
data class EngineErrorPayload(
    val code: String,
    val message: String,
)

sealed interface EngineRequest {
    val id: String

    data class GetSettings(override val id: String) : EngineRequest
    data class SaveSettings(override val id: String, val settings: EngineAppSettings) : EngineRequest
    data class GetModelStatus(override val id: String) : EngineRequest
    data class DownloadDefaultModel(override val id: String) : EngineRequest
    data class SelectModelPath(override val id: String) : EngineRequest
    data class TestMicrophone(override val id: String) : EngineRequest
    data class StartRecording(override val id: String) : EngineRequest
    data class StopRecordingAndTranscribe(override val id: String) : EngineRequest
    data class Shutdown(override val id: String) : EngineRequest
}

sealed interface EngineMessage {
    data class Result(
        val id: String,
        val ok: Boolean,
        val payload: JsonElement?,
        val error: EngineErrorPayload?,
    ) : EngineMessage

    data class Event(
        val event: EngineEventName,
        val payload: JsonElement,
    ) : EngineMessage
}

@Serializable
enum class EngineEventName {
    @SerialName("engineReady")
    EngineReady,

    @SerialName("recordingStarted")
    RecordingStarted,

    @SerialName("recordingStopped")
    RecordingStopped,

    @SerialName("transcriptionStarted")
    TranscriptionStarted,

    @SerialName("transcriptionDone")
    TranscriptionDone,

    @SerialName("transcriptionFailed")
    TranscriptionFailed,

    @SerialName("overlayStatusChanged")
    OverlayStatusChanged,
}

object EngineJson {
    val json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = true
    }

    fun encodeRequestLine(request: EngineRequest): String {
        val encoded = when (request) {
            is EngineRequest.GetSettings -> requestObject(request.id, "getSettings")
            is EngineRequest.SaveSettings -> requestObject(request.id, "saveSettings") {
                put("settings", json.encodeToJsonElement(EngineAppSettings.serializer(), request.settings))
            }
            is EngineRequest.GetModelStatus -> requestObject(request.id, "getModelStatus")
            is EngineRequest.DownloadDefaultModel -> requestObject(request.id, "downloadDefaultModel")
            is EngineRequest.SelectModelPath -> requestObject(request.id, "selectModelPath")
            is EngineRequest.TestMicrophone -> requestObject(request.id, "testMicrophone")
            is EngineRequest.StartRecording -> requestObject(request.id, "startRecording")
            is EngineRequest.StopRecordingAndTranscribe -> requestObject(request.id, "stopRecordingAndTranscribe")
            is EngineRequest.Shutdown -> requestObject(request.id, "shutdown")
        }

        return json.encodeToString(encoded) + "\n"
    }

    fun decodeMessage(line: String): EngineMessage {
        val root = json.parseToJsonElement(line).jsonObject
        return when (root.getValue("type").jsonPrimitive.contentOrNull) {
            "result" -> EngineMessage.Result(
                id = root.getValue("id").jsonPrimitive.content,
                ok = root.getValue("ok").jsonPrimitive.boolean,
                payload = root["payload"]?.takeUnless { it is JsonNull },
                error = root["error"]?.let { json.decodeFromJsonElement(EngineErrorPayload.serializer(), it) },
            )
            "event" -> EngineMessage.Event(
                event = json.decodeFromJsonElement(EngineEventName.serializer(), root.getValue("event")),
                payload = root["payload"] ?: buildJsonObject {},
            )
            else -> error("Unknown engine message type: ${root["type"]}")
        }
    }
}

inline fun <reified T> EngineMessage.Result.payloadAs(): T {
    val payload = requireNotNull(payload) { "Engine result has no payload" }
    return EngineJson.json.decodeFromJsonElement(payload)
}

fun newCommandId(): String = newUuidString()

@OptIn(ExperimentalUuidApi::class)
private fun newUuidString(): String = Uuid.random().toString()

fun EngineAppSettings.toDomain() = AppSettings(
    hotkey = hotkey,
    modelPath = modelPath,
    microphoneDeviceId = microphoneDeviceId,
    glossaryTerms = glossaryTerms,
    restoreClipboard = restoreClipboard,
)

fun AppSettings.toEngine() = EngineAppSettings(
    hotkey = hotkey,
    modelPath = modelPath,
    microphoneDeviceId = microphoneDeviceId,
    glossaryTerms = glossaryTerms,
    restoreClipboard = restoreClipboard,
)

fun EngineModelStatus.toDomain() = ModelStatus(
    configuredPath = configuredPath,
    defaultModelPath = defaultModelPath,
    defaultModelExists = defaultModelExists,
    configuredModelExists = configuredModelExists,
)

fun EngineMicrophoneStatus.toDomain() = MicrophoneStatus(
    defaultDevice = defaultDevice,
    devices = devices,
)

fun EngineOverlayPayload.toDomain() = OverlayStatus(
    state = state,
    message = message,
)

private fun requestObject(
    id: String,
    command: String,
    extra: JsonObjectBuilder.() -> Unit = {},
) = buildJsonObject {
    put("id", id)
    put("command", command)
    extra()
}
