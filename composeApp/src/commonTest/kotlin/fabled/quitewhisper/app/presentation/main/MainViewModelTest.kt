package fabled.quitewhisper.app.presentation.main

import fabled.quitewhisper.app.domain.AppSettings
import fabled.quitewhisper.app.domain.DictationRepository
import fabled.quitewhisper.app.domain.EngineEvent
import fabled.quitewhisper.app.domain.HotkeyEvent
import fabled.quitewhisper.app.domain.MicrophoneStatus
import fabled.quitewhisper.app.domain.ModelStatus
import fabled.quitewhisper.app.domain.OverlayStatus
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
        val viewModel =
            MainViewModel(repository)

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
        val viewModel = MainViewModel(
            FakeDictationRepository()
        )

        viewModel.onAction(MainAction.HideMainWindow)

        assertFalse(viewModel.state.value.mainWindowVisible)

        viewModel.onAction(MainAction.ShowMainWindow)

        assertTrue(viewModel.state.value.mainWindowVisible)
    }

    @Test
    fun hotkeyPressAndReleaseStartsAndStopsRecording() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = FakeDictationRepository()
        val viewModel =
            MainViewModel(repository)

        repository.hotkeyEvents.emit(HotkeyEvent.Pressed)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.recording)

        repository.hotkeyEvents.emit(HotkeyEvent.Released)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.recording)
        assertEquals(1, repository.startRecordingCalls)
        assertEquals(1, repository.stopRecordingCalls)
    }

    @Test
    fun startRecordingActionStartsRecording() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = FakeDictationRepository()
        val viewModel =
            MainViewModel(repository)

        viewModel.onAction(MainAction.StartRecording)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.recording)
        assertEquals("Listening...", viewModel.state.value.status)
        assertEquals(1, repository.startRecordingCalls)
    }

    @Test
    fun failedStartRecordingActionUpdatesStatus() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = FakeDictationRepository().apply {
            startRecordingError = IllegalStateException("No input device is available")
        }
        val viewModel =
            MainViewModel(repository)

        viewModel.onAction(MainAction.StartRecording)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.recording)
        assertEquals("No input device is available", viewModel.state.value.status)
        assertEquals(1, repository.startRecordingCalls)
    }

    @Test
    fun failedHotkeyRecordingStartDoesNotTriggerStopOnRelease() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = FakeDictationRepository().apply {
            startRecordingError = IllegalStateException("No input device is available")
        }
        val viewModel =
            MainViewModel(repository)

        repository.hotkeyEvents.emit(HotkeyEvent.Pressed)
        advanceUntilIdle()

        repository.hotkeyEvents.emit(HotkeyEvent.Released)
        advanceUntilIdle()

        repository.hotkeyEvents.emit(HotkeyEvent.Pressed)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.recording)
        assertEquals("No input device is available", viewModel.state.value.status)
        assertEquals(2, repository.startRecordingCalls)
        assertEquals(0, repository.stopRecordingCalls)
    }
}

private class ThrowingDictationRepository(
    private val message: String,
) : DictationRepository {
    override val events = MutableSharedFlow<EngineEvent>()
    override val hotkeyEvents = MutableSharedFlow<HotkeyEvent>()

    override suspend fun start() = Unit

    override fun close() = Unit

    override suspend fun getSettings(): AppSettings = error(message)

    override suspend fun saveSettings(settings: AppSettings) = error(message)

    override suspend fun startHotkey(hotkey: String) = error(message)

    override suspend fun getModelStatus(): ModelStatus = error(message)

    override suspend fun downloadDefaultModel(): ModelStatus = error(message)

    override suspend fun testMicrophone(): MicrophoneStatus = error(message)

    override suspend fun startRecording() = error(message)

    override suspend fun stopRecordingAndTranscribe() = error(message)
}

private class FakeDictationRepository : DictationRepository {
    override val events = MutableSharedFlow<EngineEvent>(replay = 1)
    override val hotkeyEvents = MutableSharedFlow<HotkeyEvent>(replay = 1)
    var startRecordingCalls = 0
    var stopRecordingCalls = 0
    var startRecordingError: Throwable? = null

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

    override suspend fun startHotkey(hotkey: String) = Unit

    override suspend fun getModelStatus() = ModelStatus(
        configuredPath = null,
        defaultModelPath = "model.bin",
        defaultModelExists = false,
        configuredModelExists = false,
    )

    override suspend fun downloadDefaultModel() = getModelStatus()

    override suspend fun testMicrophone() = MicrophoneStatus(defaultDevice = "Default", devices = listOf("Default"))

    override suspend fun startRecording() {
        startRecordingCalls += 1
        startRecordingError?.let { throw it }
    }

    override suspend fun stopRecordingAndTranscribe() {
        stopRecordingCalls += 1
    }
}
