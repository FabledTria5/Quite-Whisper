# Compose Shell Prototype

QuiteWhisper now uses a Compose Multiplatform desktop shell backed by a separate Rust dictation engine sidecar.

## Architecture

- `engine/src/engine.rs` defines the JSON Lines IPC contract and runtime for the Rust dictation engine.
- `engine/src/bin/quite-whisper-engine.rs` exposes the Rust engine as a fabled sidecar process.
- `composeApp/` contains the Compose Desktop prototype.
- Compose talks to Rust through stdin/stdout JSON Lines. Each command carries an `id`; results echo the same `id`; events are emitted independently.

## Run Locally

Run the Compose desktop shell from the repo root:

```powershell
gradle :composeApp:run
```

The Gradle task builds the Rust sidecar in release mode and copies it into Compose application resources before launching the app.

For packaged Compose builds:

```powershell
gradle :composeApp:createDistributable
gradle :composeApp:packageDistributionForCurrentOS
```

Compose packages the sidecar through `nativeDistributions.appResourcesRootDir`. At runtime, the app first looks for the engine in `compose.application.resources.dir`, then falls back to dev paths:

```text
<compose.application.resources.dir>/quite-whisper-engine.exe
engine/target/debug/quite-whisper-engine.exe
engine/target/release/quite-whisper-engine.exe
quite-whisper-engine.exe
```

To use a custom engine binary:

```powershell
$env:QUITEWHISPER_ENGINE_PATH = "C:\path\to\quite-whisper-engine.exe"
gradle :composeApp:run
```

## Current Scope

Implemented:

- engine startup handshake
- settings read/write
- model status
- default model download command
- microphone status command
- manual start/stop recording buttons
- global push-to-talk shortcut events from the Rust sidecar
- bottom-center recording/transcription overlay chip
- Compose NativeTray tray icon with open, recording, microphone check, and quit actions
- close-to-tray window behavior
- bundled sidecar resources for Compose run/distributable tasks
- engine event log

Still intentionally left for later:

- full installer polish/signing/release workflow
- macOS SwiftUI/AppKit islands

## Verification

```powershell
.\scripts\windows-dev.ps1 cargo test --test engine_protocol
gradle :composeApp:test
gradle :composeApp:createDistributable
```

Minimal IPC smoke:

```powershell
'{"id":"cmd-shutdown","command":"shutdown"}' | .\engine\target\release\quite-whisper-engine.exe
```
