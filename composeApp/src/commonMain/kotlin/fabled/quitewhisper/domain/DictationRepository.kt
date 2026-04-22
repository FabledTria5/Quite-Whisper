package fabled.quitewhisper.domain

import kotlinx.coroutines.flow.Flow

interface DictationRepository {
    val events: Flow<EngineEvent>
    val hotkeyEvents: Flow<HotkeyEvent>

    suspend fun start()

    fun close()

    suspend fun getSettings(): AppSettings

    suspend fun saveSettings(settings: AppSettings)

    suspend fun startHotkey(hotkey: String)

    suspend fun getModelStatus(): ModelStatus

    suspend fun downloadDefaultModel(): ModelStatus

    suspend fun testMicrophone(): MicrophoneStatus

    suspend fun startRecording()

    suspend fun stopRecordingAndTranscribe()
}
