package fabled.quitewhisper.app.presentation.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fabled.quitewhisper.core.designsystem.QuiteWhisperTheme

@Composable
fun MainScreen(
    state: MainState,
    onAction: (MainAction) -> Unit,
) {
    QuiteWhisperTheme {
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
private fun Header(state: MainState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("QuiteWhisper", style = MaterialTheme.typography.headlineMedium)
        Text("Compose shell prototype", style = MaterialTheme.typography.labelLarge)
        Text("Engine: ${state.engineStatus}")
        Text(state.status)
    }
}

@Composable
private fun RecordingPanel(
    state: MainState,
    onAction: (MainAction) -> Unit,
) {
    Section(title = "Recording") {
        OutlinedTextField(
            value = state.hotkeyDraft,
            onValueChange = { onAction(MainAction.HotkeyChanged(it)) },
            label = { Text("Push-to-talk hotkey") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { onAction(MainAction.SaveSettings) }) {
                Text("Save settings")
            }
            Button(
                onClick = { onAction(MainAction.StartRecording) },
                enabled = !state.recording,
            ) {
                Text("Start recording")
            }
            Button(
                onClick = { onAction(MainAction.StopRecordingAndTranscribe) },
                enabled = state.recording,
            ) {
                Text("Stop and transcribe")
            }
        }
    }
}

@Composable
private fun ModelPanel(
    state: MainState,
    onAction: (MainAction) -> Unit,
) {
    Section(title = "Speech model") {
        OutlinedTextField(
            value = state.modelPathDraft,
            onValueChange = { onAction(MainAction.ModelPathChanged(it)) },
            label = { Text("Model path") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Button(onClick = { onAction(MainAction.DownloadDefaultModel) }) {
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
    state: MainState,
    onAction: (MainAction) -> Unit,
) {
    Section(title = "Microphone") {
        Button(onClick = { onAction(MainAction.TestMicrophone) }) {
            Text("Check microphone")
        }
        Text("Default: ${state.microphoneStatus?.defaultDevice ?: "not checked"}")
    }
}

@Composable
private fun GlossaryPanel(
    state: MainState,
    onAction: (MainAction) -> Unit,
) {
    Section(title = "Technical glossary") {
        OutlinedTextField(
            value = state.glossaryDraft,
            onValueChange = { onAction(MainAction.GlossaryChanged(it)) },
            label = { Text("One term per line") },
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            minLines = 6,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = state.restoreClipboardDraft,
                onCheckedChange = { onAction(MainAction.RestoreClipboardChanged(it)) },
            )
            Text("Restore clipboard after paste")
        }
    }
}

@Composable
private fun EventLog(state: MainState) {
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
