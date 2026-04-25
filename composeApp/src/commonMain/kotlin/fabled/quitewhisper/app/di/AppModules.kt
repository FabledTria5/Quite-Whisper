package fabled.quitewhisper.app.di

import fabled.quitewhisper.app.data.EngineDictationRepository
import fabled.quitewhisper.app.domain.DictationRepository
import fabled.quitewhisper.app.presentation.main.MainViewModel
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val dictationDataModule = module {
    singleOf(::EngineDictationRepository) {
        bind<DictationRepository>()
    }
}

val mainPresentationModule = module {
    viewModelOf(::MainViewModel)
}
