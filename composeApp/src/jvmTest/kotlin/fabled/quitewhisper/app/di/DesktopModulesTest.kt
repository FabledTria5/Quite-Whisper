package fabled.quitewhisper.app.di

import fabled.quitewhisper.app.data.engine.EngineConnection
import fabled.quitewhisper.app.data.engine.HotkeyConnection
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
        assertIs<HotkeyConnection>(koin.get<HotkeyConnection>())
    }
}
