package fabled.quitewhisper.di

import fabled.quitewhisper.data.engine.EngineConnection
import org.koin.core.context.GlobalContext.stopKoin
import org.koin.core.context.startKoin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertIs

class DesktopModulesTest {
    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun desktopEngineModuleProvidesEngineConnectionWithoutExternalPathBinding() {
        val koin = startKoin {
            modules(desktopEngineModule)
        }.koin

        assertIs<EngineConnection>(koin.get<EngineConnection>())
    }
}
