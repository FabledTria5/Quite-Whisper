package fabled.quitewhisper.app.data.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EngineProtocolTest {
    @Test
    fun encodesGetSettingsRequestAsJsonLine() {
        val request = EngineRequest.GetSettings(id = "cmd-1")

        assertEquals(
            """{"id":"cmd-1","command":"getSettings"}""" + "\n",
            EngineJson.encodeRequestLine(request),
        )
    }

    @Test
    fun encodesSaveSettingsRequestWithSnakeCasePayload() {
        val request = EngineRequest.SaveSettings(
            id = "cmd-2",
            settings = EngineAppSettings(
                hotkey = "Control+Alt+Space",
                modelPath = "C:/models/ggml.bin",
                microphoneDeviceId = null,
                glossaryTerms = listOf("Kotlin", "Rust"),
                restoreClipboard = true,
            ),
        )

        assertEquals(
            """{"id":"cmd-2","command":"saveSettings","settings":{"hotkey":"Control+Alt+Space","model_path":"C:/models/ggml.bin","microphone_device_id":null,"glossary_terms":["Kotlin","Rust"],"restore_clipboard":true}}""" + "\n",
            EngineJson.encodeRequestLine(request),
        )
    }

    @Test
    fun decodesEngineReadyEvent() {
        val message = EngineJson.decodeMessage("""{"type":"event","event":"engineReady","payload":{}}""")

        val event = assertIs<EngineMessage.Event>(message)
        assertEquals(EngineEventName.EngineReady, event.event)
    }

    @Test
    fun decodesSettingsResultPayload() {
        val message = EngineJson.decodeMessage(
            """{"type":"result","id":"cmd-3","ok":true,"payload":{"hotkey":"Control+Alt+Space","model_path":null,"microphone_device_id":null,"glossary_terms":["Kotlin"],"restore_clipboard":false}}""",
        )

        val result = assertIs<EngineMessage.Result>(message)
        assertTrue(result.ok)
        assertEquals("cmd-3", result.id)
        assertEquals(
            EngineAppSettings(
                hotkey = "Control+Alt+Space",
                modelPath = null,
                microphoneDeviceId = null,
                glossaryTerms = listOf("Kotlin"),
                restoreClipboard = false,
            ),
            result.payloadAs<EngineAppSettings>(),
        )
    }

    @Test
    fun decodesTypedErrorResult() {
        val message = EngineJson.decodeMessage(
            """{"type":"result","id":"cmd-4","ok":false,"error":{"code":"microphone_error","message":"No input device"}}""",
        )

        val result = assertIs<EngineMessage.Result>(message)
        assertFalse(result.ok)
        assertEquals(EngineErrorPayload("microphone_error", "No input device"), result.error)
    }
}
