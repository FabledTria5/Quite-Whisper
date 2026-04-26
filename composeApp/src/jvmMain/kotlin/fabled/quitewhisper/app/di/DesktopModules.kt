package fabled.quitewhisper.app.di

import fabled.quitewhisper.app.data.engine.EngineConnection
import fabled.quitewhisper.app.data.engine.HotkeyClient
import fabled.quitewhisper.app.data.engine.HotkeyConnection
import fabled.quitewhisper.app.data.engine.ProcessEngineConnection
import org.koin.dsl.module

val desktopEngineModule = module {
    single<EngineConnection> { ProcessEngineConnection() }
    single<HotkeyConnection> { HotkeyClient() }
}
