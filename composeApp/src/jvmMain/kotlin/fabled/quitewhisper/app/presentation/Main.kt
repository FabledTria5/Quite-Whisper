package fabled.quitewhisper.app.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.application
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import fabled.quitewhisper.app.di.desktopEngineModule
import fabled.quitewhisper.app.di.dictationDataModule
import fabled.quitewhisper.app.di.mainPresentationModule
import fabled.quitewhisper.app.presentation.components.QuiteWhisperDesktopRoot
import org.koin.compose.KoinApplication
import org.koin.core.context.GlobalContext.stopKoin
import org.koin.dsl.koinConfiguration

private class ComposeViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore = ViewModelStore()
    fun dispose() {
        viewModelStore.clear()
    }
}

@Composable
private fun rememberViewModelStoreOwner(): ViewModelStoreOwner {
    val viewModelStoreOwner = remember { ComposeViewModelStoreOwner() }
    DisposableEffect(key1 = viewModelStoreOwner) {
        onDispose { viewModelStoreOwner.dispose() }
    }
    return viewModelStoreOwner
}

fun main() {
    if (System.getProperty("os.name").lowercase().contains(other = "win")) {
        System.setProperty("skiko.renderApi", "OPENGL")
    }

    application {
        KoinApplication(
            configuration = koinConfiguration {
                modules(
                    desktopEngineModule,
                    dictationDataModule,
                    mainPresentationModule,
                )
            }
        ) {
            val viewModelStoreOwner = rememberViewModelStoreOwner()

            CompositionLocalProvider(
                value = LocalViewModelStoreOwner provides viewModelStoreOwner
            ) {
                QuiteWhisperDesktopRoot(
                    onExitApplicationRequest = {
                        stopKoin()
                        exitApplication()
                    }
                )
            }
        }
    }
}