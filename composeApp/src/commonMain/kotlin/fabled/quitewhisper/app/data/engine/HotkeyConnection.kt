package fabled.quitewhisper.app.data.engine

import kotlinx.coroutines.flow.SharedFlow

sealed interface HotkeyEvent {
    data object Pressed : HotkeyEvent
    data object Released : HotkeyEvent
    data class Error(val message: String) : HotkeyEvent
}

interface HotkeyConnection {
    val events: SharedFlow<HotkeyEvent>

    suspend fun start(hotkey: String)

    fun close()
}
