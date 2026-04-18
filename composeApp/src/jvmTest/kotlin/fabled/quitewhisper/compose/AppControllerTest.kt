package fabled.quitewhisper.compose

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import fabled.quitewhisper.compose.engine.EngineConnection
import fabled.quitewhisper.compose.engine.EngineEventName
import fabled.quitewhisper.compose.engine.EngineJson
import fabled.quitewhisper.compose.engine.EngineMessage
import fabled.quitewhisper.compose.engine.EngineRequest
import fabled.quitewhisper.compose.engine.OverlayPayload
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppControllerTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun microphoneCheckFailureUpdatesStatusInsteadOfEscapingCoroutine() = runTest {
        val controller = AppController(
            scope = this,
            engineClient = ThrowingEngineConnection("No input device is available"),
        )

        controller.onAction(AppAction.TestMicrophone)
        advanceUntilIdle()

        assertEquals("No input device is available", controller.state.value.status)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun overlayStatusEventUpdatesChipState() = runTest {
        val engine = FakeEngineConnection()
        val controller = AppController(scope = this, engineClient = engine)

        controller.onEngineMessage(
            EngineMessage.Event(
                event = EngineEventName.OverlayStatusChanged,
                payload = EngineJson.json.encodeToJsonElement(
                    OverlayPayload(state = "listening", message = "Listening"),
                ),
            ),
        )

        assertEquals(
            OverlayPayload(state = "listening", message = "Listening"),
            controller.state.value.overlay,
        )
    }

    @Test
    fun closeToTrayHidesMainWindowWithoutStoppingController() {
        val controller = AppController(
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()),
            engineClient = FakeEngineConnection(),
        )

        controller.onAction(AppAction.HideMainWindow)

        assertFalse(controller.state.value.mainWindowVisible)

        controller.onAction(AppAction.ShowMainWindow)

        assertTrue(controller.state.value.mainWindowVisible)
    }
}

private class ThrowingEngineConnection(
    private val message: String,
) : EngineConnection {
    override val messages = MutableSharedFlow<EngineMessage>()

    override suspend fun start() = Unit

    override suspend fun send(
        request: EngineRequest,
        timeoutMillis: Long,
    ): EngineMessage.Result = error(message)

    override fun close() = Unit
}

private class FakeEngineConnection : EngineConnection {
    override val messages = MutableSharedFlow<EngineMessage>(replay = 1)

    override suspend fun start() = Unit

    override suspend fun send(
        request: EngineRequest,
        timeoutMillis: Long,
    ): EngineMessage.Result {
        val payload = when (request) {
            is EngineRequest.GetSettings -> EngineJson.json.encodeToJsonElement(
                fabled.quitewhisper.compose.engine.AppSettings(
                    hotkey = "Control+Alt+Space",
                    modelPath = null,
                    microphoneDeviceId = null,
                    glossaryTerms = emptyList(),
                    restoreClipboard = true,
                ),
            )
            is EngineRequest.GetModelStatus -> EngineJson.json.encodeToJsonElement(
                fabled.quitewhisper.compose.engine.ModelStatus(
                    configuredPath = null,
                    defaultModelPath = "model.bin",
                    defaultModelExists = false,
                    configuredModelExists = false,
                ),
            )
            else -> EngineJson.json.encodeToJsonElement(emptyMap<String, String>())
        }

        return EngineMessage.Result(
            id = request.id,
            ok = true,
            payload = payload,
            error = null,
        )
    }

    override fun close() = Unit
}
