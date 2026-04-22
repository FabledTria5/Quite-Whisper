package fabled.quitewhisper.di

import fabled.quitewhisper.data.engine.EngineConnection
import fabled.quitewhisper.data.engine.HotkeyClient
import fabled.quitewhisper.data.engine.HotkeyConnection
import fabled.quitewhisper.data.engine.ProcessEngineConnection
import org.koin.dsl.module

val desktopEngineModule = module {
    single<EngineConnection> { ProcessEngineConnection() }
    single<HotkeyConnection> { HotkeyClient() }
}
