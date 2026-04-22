package fabled.quitewhisper.presentation.main

import fabled.quitewhisper.domain.AppSettings
import fabled.quitewhisper.domain.DictationRepository
import fabled.quitewhisper.domain.EngineEvent
import fabled.quitewhisper.domain.MicrophoneStatus
import fabled.quitewhisper.domain.ModelStatus
import fabled.quitewhisper.domain.OverlayStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    @Test
    fun microphoneCheckFailureUpdatesStatusInsteadOfEscapingCoroutine() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val viewModel = MainViewModel(
            repository = ThrowingDictationRepository("No input device is available"),
        )

        viewModel.onAction(MainAction.TestMicrophone)
        advanceUntilIdle()

        assertEquals("No input device is available", viewModel.state.value.status)
    }

    @Test
    fun overlayStatusEventUpdatesChipState() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = FakeDictationRepository()
        val viewModel = MainViewModel(repository)

        repository.events.emit(
            EngineEvent.OverlayChanged(
                OverlayStatus(state = "listening", message = "Listening"),
            ),
        )
        advanceUntilIdle()

        assertEquals(
            OverlayStatus(state = "listening", message = "Listening"),
            viewModel.state.value.overlay,
        )
    }

    @Test
    fun closeToTrayHidesMainWindowWithoutStoppingViewModel() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val viewModel = MainViewModel(FakeDictationRepository())

        viewModel.onAction(MainAction.HideMainWindow)

        assertFalse(viewModel.state.value.mainWindowVisible)

        viewModel.onAction(MainAction.ShowMainWindow)

        assertTrue(viewModel.state.value.mainWindowVisible)
    }
}

private class ThrowingDictationRepository(
    private val message: String,
) : DictationRepository {
    override val events = MutableSharedFlow<EngineEvent>()

    override suspend fun start() = Unit

    override fun close() = Unit

    override suspend fun getSettings(): AppSettings = error(message)

    override suspend fun saveSettings(settings: AppSettings) = error(message)

    override suspend fun getModelStatus(): ModelStatus = error(message)

    override suspend fun downloadDefaultModel(): ModelStatus = error(message)

    override suspend fun testMicrophone(): MicrophoneStatus = error(message)

    override suspend fun startRecording() = error(message)

    override suspend fun stopRecordingAndTranscribe() = error(message)
}

private class FakeDictationRepository : DictationRepository {
    override val events = MutableSharedFlow<EngineEvent>(replay = 1)

    override suspend fun start() = Unit

    override fun close() = Unit

    override suspend fun getSettings() = AppSettings(
        hotkey = "Control+Alt+Space",
        modelPath = null,
        microphoneDeviceId = null,
        glossaryTerms = emptyList(),
        restoreClipboard = true,
    )

    override suspend fun saveSettings(settings: AppSettings) = Unit

    override suspend fun getModelStatus() = ModelStatus(
        configuredPath = null,
        defaultModelPath = "model.bin",
        defaultModelExists = false,
        configuredModelExists = false,
    )

    override suspend fun downloadDefaultModel() = getModelStatus()

    override suspend fun testMicrophone() = MicrophoneStatus(defaultDevice = "Default", devices = listOf("Default"))

    override suspend fun startRecording() = Unit

    override suspend fun stopRecordingAndTranscribe() = Unit
}
