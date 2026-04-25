package fabled.quitewhisper.app.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.DialogState
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import fabled.quitewhisper.app.presentation.main.MainAction
import fabled.quitewhisper.app.presentation.main.MainScreen
import fabled.quitewhisper.app.presentation.main.MainViewModel
import fabled.quitewhisper.app.presentation.main.RecordingOverlayChip
import org.koin.compose.koinInject

@Composable
internal fun ApplicationScope.QuiteWhisperDesktopRoot(
    viewModel: MainViewModel = koinInject(),
    onExitApplicationRequest: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    DisposableEffect(key1 = viewModel) {
        viewModel.start()
        onDispose { viewModel.close() }
    }

    QuiteWhisperTray(
        state = state,
        onAction = viewModel::onAction,
        onExitRequest = {
            viewModel.close()
            onExitApplicationRequest()
        }
    )

    if (state.mainWindowVisible) {
        Window(
            title = "QuiteWhisper",
            onCloseRequest = { viewModel.onAction(MainAction.HideMainWindow) }
        ) {
            MainScreen(
                state = state,
                onAction = viewModel::onAction
            )
        }
    }

    state.overlay?.also { overlay ->
        DialogWindow(
            title = "",
            onCloseRequest = {},
            state = DialogState(
                width = 380.dp,
                height = 84.dp,
                position = WindowPosition(alignment = Alignment.BottomCenter)
            ),
            undecorated = true,
            transparent = true,
            alwaysOnTop = true,
            resizable = false
        ) {
            RecordingOverlayChip(payload = overlay)
        }
    }
}