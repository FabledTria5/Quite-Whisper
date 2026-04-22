package fabled.quitewhisper.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.DialogState
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.kdroid.composetray.tray.api.Tray
import fabled.quitewhisper.di.desktopEngineModule
import fabled.quitewhisper.di.dictationDataModule
import fabled.quitewhisper.di.mainPresentationModule
import fabled.quitewhisper.presentation.main.MainAction
import fabled.quitewhisper.presentation.main.MainScreen
import fabled.quitewhisper.presentation.main.MainState
import fabled.quitewhisper.presentation.main.MainViewModel
import fabled.quitewhisper.presentation.main.RecordingOverlayChip
import org.koin.compose.KoinApplication
import org.koin.compose.viewmodel.koinViewModel
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
//    System.setProperty("skiko.renderApi", "OPENGL")

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
                    onExitApplication = {
                        stopKoin()
                        exitApplication()
                    }
                )
            }
        }
    }
}

@Composable
private fun ApplicationScope.QuiteWhisperDesktopRoot(
    onExitApplication: () -> Unit,
    viewModel: MainViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    DisposableEffect(viewModel) {
        viewModel.start()
        onDispose { viewModel.close() }
    }

    QuiteWhisperTray(
        state = state,
        onAction = viewModel::onAction,
        onExit = {
            viewModel.close()
            onExitApplication()
        }
    )

    if (state.mainWindowVisible) {
        Window(
            onCloseRequest = { viewModel.onAction(MainAction.HideMainWindow) },
            title = "QuiteWhisper Compose",
        ) {
            MainScreen(
                state = state,
                onAction = viewModel::onAction,
            )
        }
    }

    val overlay = state.overlay
    if (overlay != null) {
        DialogWindow(
            onCloseRequest = {},
            title = "QuiteWhisper",
            state = DialogState(
                position = WindowPosition(Alignment.BottomCenter),
                width = 380.dp,
                height = 84.dp,
            ),
            undecorated = true,
            transparent = true,
            alwaysOnTop = true,
            resizable = false,
        ) {
            RecordingOverlayChip(overlay)
        }
    }
}

@Composable
private fun ApplicationScope.QuiteWhisperTray(
    state: MainState,
    onAction: (MainAction) -> Unit,
    onExit: () -> Unit,
) {
    Tray(
        iconContent = {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawQuiteWhisperTrayIcon(isRecording = state.recording)
            }
        },
        tooltip = "QuiteWhisper - ${state.status}",
        primaryAction = { onAction(MainAction.ShowMainWindow) },
    ) {
        Item("Open QuiteWhisper") {
            onAction(MainAction.ShowMainWindow)
        }
        Item(if (state.recording) "Stop recording" else "Start recording") {
            onAction(if (state.recording) MainAction.StopRecordingAndTranscribe else MainAction.StartRecording)
        }
        Divider()
        Item("Check microphone") {
            onAction(MainAction.TestMicrophone)
        }
        Divider()
        Item("Quit") {
            onExit()
        }
    }
}

private fun DrawScope.drawQuiteWhisperTrayIcon(isRecording: Boolean) {
    drawCircle(color = if (isRecording) Color(0xFFFF6B6B) else Color(0xFF28724F))
    drawCircle(color = Color.White, radius = size.minDimension * 0.28f)
}
