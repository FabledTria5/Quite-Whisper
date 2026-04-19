package fabled.quitewhisper.data.engine

import kotlinx.coroutines.flow.SharedFlow

interface EngineConnection {
    val messages: SharedFlow<EngineMessage>

    suspend fun start()

    suspend fun send(
        request: EngineRequest,
        timeoutMillis: Long = 30_000,
    ): EngineMessage.Result

    fun close()
}
