# QuiteWhisper Agent Notes

## Project

QuiteWhisper is a local push-to-talk dictation app on Windows first, with macOS kept in the architecture. The UI is Compose Desktop and the Rust dictation engine lives in a separate `engine/` crate.

Core behavior:
- Hold `Control+Alt+Space` to record.
- Release the hotkey to transcribe locally with Whisper.
- Paste the transcript into the active field through clipboard + paste shortcut.
- Restore the previous clipboard text when enabled.
- Use a small bottom-center overlay for listening/transcribing/pasted/error states.
- Keep dictation active when the settings window is closed; the tray menu reopens the window or exits the app.
- Use a glossary + Whisper initial prompt for technical terms. Do not add a local LLM unless explicitly requested.

## Stack

- Frontend: Compose Desktop.
- Desktop shell: Compose Desktop.
- Backend: Rust engine crate in `engine/`.
- ASR: `whisper-rs` / `whisper.cpp`.
- Audio input: `cpal`.
- Clipboard: `arboard`.
- Synthetic paste: `enigo`.

Important files:
- Compose app: `composeApp/src/main/kotlin/local/quitewhisper/compose/Main.kt`
- Compose engine client: `composeApp/src/main/kotlin/local/quitewhisper/compose/engine/EngineClient.kt`
- Engine IPC/runtime: `engine/src/engine.rs`
- Audio pipeline: `engine/src/audio.rs`
- Whisper pipeline: `engine/src/speech.rs`
- Settings: `engine/src/settings.rs`
- Model download/status: `engine/src/model.rs`
- Clipboard paste: `engine/src/inserter.rs`
- Hotkey listener: `engine/src/hotkey.rs`
- Windows helper: `scripts/windows-dev.ps1`

## Windows Toolchain

This project requires these tools on Windows:
- Rust/Cargo through rustup.
- Visual Studio Build Tools 2022 with MSVC.
- LLVM, because `whisper-rs-sys` needs `libclang.dll`.
- CMake.
- Ninja.

Use the helper script from the repo root so all required paths/env vars are set:

```powershell
.\scripts\windows-dev.ps1 cargo check
.\scripts\windows-dev.ps1 cargo test
gradle :composeApp:test
gradle :composeApp:packageDistributionForCurrentOS
```

The helper sets:
- `LIBCLANG_PATH=C:\Program Files\LLVM\bin`
- `CMAKE_GENERATOR=Ninja`
- Rust, LLVM, CMake, and Ninja paths.
- Visual Studio Developer Command Prompt environment.

## Verified State

The project has been verified on Windows with:

```powershell
.\scripts\windows-dev.ps1 cargo check
.\scripts\windows-dev.ps1 cargo test
gradle :composeApp:test
gradle :composeApp:packageDistributionForCurrentOS
```

The Compose package build produces:
- `composeApp/build/compose/binaries/main/msi/QuiteWhisper-1.0.0.msi`
- `engine/target/release/quite-whisper-engine.exe`

## Runtime Notes

The real dictation flow still needs manual testing with a microphone and a Whisper model:

1. Run the app.
2. Download the default model or select an existing `.bin` model.
3. Grant OS microphone/input permissions if prompted.
4. Focus a text field in another app.
5. Hold `Control+Alt+Space`, speak, release.
6. Verify overlay state, transcription, paste behavior, and clipboard restore.

The default model is:

```text
ggml-small-q5_1.bin
```

It is downloaded from Hugging Face through `engine/src/model.rs`.

## Implementation Constraints

- Keep the MVP local-only. Do not introduce cloud transcription.
- Keep the LLM post-edit path as a future extension point via `TextPostProcessor`; do not add Gemma/Qwen unless requested.
- Do not replace the clipboard insertion strategy without testing against common Windows apps.
- Be careful with global shortcut changes; Compose receives press/release events from `engine/src/hotkey.rs`.
- The repo may not be initialized as git. Check before using git commands.
- Avoid committing build outputs from `target/`, `engine/target/`, or `composeApp/build/`.

## Known Follow-Ups

- Manual test first-run model download.
- Manual test microphone capture and latency.
- Manual test paste/clipboard restoration across editors, browsers, IDEs, and messengers.
- Compare `ggml-small-q5_1.bin` latency/quality against `ggml-base-q5_1.bin` and `ggml-large-v3-turbo-q5_0.bin`.
- Add macOS permissions notes and verify macOS packaging later.
