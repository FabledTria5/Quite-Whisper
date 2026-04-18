package fabled.quitewhisper.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.kdroid.composetray.tray.api.Tray
import fabled.quitewhisper.compose.engine.OverlayPayload

fun main()  {
    System.setProperty("skiko.renderApi", "OPENGL")

    application {
        val scope = rememberCoroutineScope()
        val controller = remember { AppController(scope) }

        val state by controller.state.collectAsState()

        DisposableEffect(Unit) {
            controller.start()
            onDispose { controller.close() }
        }

        QuiteWhisperTray(
            state = state,
            onAction = controller::onAction,
            onExit = {
                controller.close()
                exitApplication()
            }
        )

        if (state.mainWindowVisible) {
            Window(
                onCloseRequest = { controller.onAction(AppAction.HideMainWindow) },
                title = "QuiteWhisper Compose",
            ) {
                QuiteWhisperApp(
                    state = state,
                    onAction = controller::onAction,
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
                resizable = false
            ) {
                RecordingChip(overlay)
            }
        }
    }
}

@Composable
private fun ApplicationScope.QuiteWhisperTray(
    state: AppUiState,
    onAction: (AppAction) -> Unit,
    onExit: () -> Unit,
) {
    Tray(
        iconContent = {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawQuiteWhisperTrayIcon(isRecording = state.recording)
            }
        },
        tooltip = "QuiteWhisper - ${state.status}",
        primaryAction = { onAction(AppAction.ShowMainWindow) },
    ) {
        Item("Open QuiteWhisper") {
            onAction(AppAction.ShowMainWindow)
        }
        Item(if (state.recording) "Stop recording" else "Start recording") {
            onAction(if (state.recording) AppAction.StopRecordingAndTranscribe else AppAction.StartRecording)
        }
        Divider()
        Item("Check microphone") {
            onAction(AppAction.TestMicrophone)
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

@Composable
fun QuiteWhisperApp(
    state: AppUiState,
    onAction: (AppAction) -> Unit,
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Header(state)
                RecordingPanel(state, onAction)
                ModelPanel(state, onAction)
                MicrophonePanel(state, onAction)
                GlossaryPanel(state, onAction)
                EventLog(state)
            }
        }
    }
}

@Composable
private fun Header(state: AppUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("QuiteWhisper", style = MaterialTheme.typography.headlineMedium)
        Text("Compose shell prototype", style = MaterialTheme.typography.labelLarge)
        Text("Engine: ${state.engineStatus}")
        Text(state.status)
    }
}

@Composable
private fun RecordingPanel(
    state: AppUiState,
    onAction: (AppAction) -> Unit,
) {
    Section(title = "Recording") {
        OutlinedTextField(
            value = state.hotkeyDraft,
            onValueChange = { onAction(AppAction.HotkeyChanged(it)) },
            label = { Text("Push-to-talk hotkey") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { onAction(AppAction.SaveSettings) }) {
                Text("Save settings")
            }
            Button(
                onClick = { onAction(AppAction.StartRecording) },
                enabled = !state.recording,
            ) {
                Text("Start recording")
            }
            Button(
                onClick = { onAction(AppAction.StopRecordingAndTranscribe) },
                enabled = state.recording,
            ) {
                Text("Stop and transcribe")
            }
        }
    }
}

@Composable
private fun ModelPanel(
    state: AppUiState,
    onAction: (AppAction) -> Unit,
) {
    Section(title = "Speech model") {
        OutlinedTextField(
            value = state.modelPathDraft,
            onValueChange = { onAction(AppAction.ModelPathChanged(it)) },
            label = { Text("Model path") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Button(onClick = { onAction(AppAction.DownloadDefaultModel) }) {
            Text("Download default model")
        }
        val modelStatus = state.modelStatus
        if (modelStatus != null) {
            Text(if (modelStatus.defaultModelExists) "Default model is downloaded." else "Default model is not downloaded yet.")
            Text(modelStatus.defaultModelPath)
        }
    }
}

@Composable
private fun MicrophonePanel(
    state: AppUiState,
    onAction: (AppAction) -> Unit,
) {
    Section(title = "Microphone") {
        Button(onClick = { onAction(AppAction.TestMicrophone) }) {
            Text("Check microphone")
        }
        Text("Default: ${state.microphoneStatus?.defaultDevice ?: "not checked"}")
    }
}

@Composable
private fun GlossaryPanel(
    state: AppUiState,
    onAction: (AppAction) -> Unit,
) {
    Section(title = "Technical glossary") {
        OutlinedTextField(
            value = state.glossaryDraft,
            onValueChange = { onAction(AppAction.GlossaryChanged(it)) },
            label = { Text("One term per line") },
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            minLines = 6,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = state.restoreClipboardDraft,
                onCheckedChange = { onAction(AppAction.RestoreClipboardChanged(it)) },
            )
            Text("Restore clipboard after paste")
        }
    }
}

@Composable
private fun EventLog(state: AppUiState) {
    Section(title = "Engine events") {
        if (state.eventLog.isEmpty()) {
            Text("No events yet.")
        } else {
            state.eventLog.forEach { event ->
                Text(event)
            }
        }
    }
}

@Composable
private fun RecordingChip(payload: OverlayPayload) {
    val dotColor = when (payload.state) {
        "transcribing" -> Color(0xFFF5C451)
        "error" -> Color(0xFFFF6B6B)
        else -> Color(0xFF55D67B)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = Color(0xE6161C18),
            contentColor = Color.White,
            shape = MaterialTheme.shapes.small,
            shadowElevation = 10.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 13.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(dotColor, CircleShape),
                )
                Text(payload.message)
            }
        }
    }
}

@Composable
private fun Section(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}
