package fabled.quitewhisper.di

import fabled.quitewhisper.data.EngineDictationRepository
import fabled.quitewhisper.domain.DictationRepository
import fabled.quitewhisper.presentation.main.MainViewModel
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
