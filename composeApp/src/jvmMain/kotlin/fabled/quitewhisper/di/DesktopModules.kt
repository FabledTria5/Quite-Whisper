package fabled.quitewhisper.di

import fabled.quitewhisper.data.engine.EngineConnection
import fabled.quitewhisper.data.engine.ProcessEngineConnection
import org.koin.dsl.module

val desktopEngineModule = module {
    single<EngineConnection> { ProcessEngineConnection() }
}
