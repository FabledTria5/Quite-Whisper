package fabled.quitewhisper.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.window.ApplicationScope
import com.kdroid.composetray.tray.api.Tray
import fabled.quitewhisper.presentation.main.MainAction
import fabled.quitewhisper.presentation.main.MainState

@Composable
internal fun ApplicationScope.QuiteWhisperTray(
    state: MainState,
    onExitRequest: () -> Unit,
    onAction: (MainAction) -> Unit
) {
    Tray(
        iconContent = {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawQuiteWhisperTrayIcon(isRecording = state.recording)
            }
        },
        tooltip = "QuiteWhisper",
        primaryAction = { onAction(MainAction.ShowMainWindow) }
    ) {
        Item(label = "Open QuiteWhisper") {
            onAction(MainAction.ShowMainWindow)
        }
        Item(label = if (state.recording) "Stop recording" else "Start recording") {
            onAction(if (state.recording) MainAction.StopRecordingAndTranscribe else MainAction.StartRecording)
        }
        Divider()
        Item(label = "Check microphone") {
            onAction(MainAction.TestMicrophone)
        }
        Divider()
        Item(label = "Quit") {
            onExitRequest()
        }
    }
}

private fun DrawScope.drawQuiteWhisperTrayIcon(isRecording: Boolean) {
    drawCircle(color = if (isRecording) Color(0xFFFF6B6B) else Color(0xFF28724F))
    drawCircle(color = Color.White, radius = size.minDimension * 0.28F)
}