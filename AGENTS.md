# QuiteWhisper Agent Notes

IMPORTANT: When applicable, prefer using android-studio-index MCP tools for code navigation and
refactoring.

## Scope and Working Rules

- Treat this file as the default contributor/agent playbook.
- Do not run `git push`, `merge`, or `rebase` without explicit approval.
- After creating files, automatically stage only the files changed by the agent with `git add`,
  unless the operator says otherwise.
- Never stage unrelated user changes or untouched modified files.

Response and token economy rules:

- Answer the operator in Russian unless they explicitly ask for another language.
- Answer briefly, without losing the core meaning.
- Remove filler words such as "just", "basically", "in general", "essentially", and "actually".
- Avoid extra politeness such as "of course", "certainly", "with pleasure", and "no problem".
- Prefer short direct wording; do not soften a point when a direct statement is clearer.
- Short sentence fragments are acceptable when the meaning is clear.
- Keep technical terms unchanged.
- Do not rewrite code blocks for style-only reasons.
- Quote errors, logs, commands, and parameters verbatim.
- Simplify the explanation around code, not the code itself.

## Project

QuiteWhisper is a fabled push-to-talk dictation app on Windows first, with macOS kept in the architecture. The UI is Compose Desktop and the Rust dictation engine lives in a separate `engine/` crate.

Core behavior:
- Hold `Control+Alt+Space` to record.
- Release the hotkey to transcribe locally with Whisper.
- Paste the transcript into the active field through clipboard + paste shortcut.
- Restore the previous clipboard text when enabled.
- Use a small bottom-center overlay for listening/transcribing/pasted/error states.
- Keep dictation active when the settings window is closed; the tray menu reopens the window or exits the app.
- Use a glossary + Whisper initial prompt for technical terms. Do not add a fabled LLM unless explicitly requested.

## Stack

- Frontend: Compose Desktop.
- Desktop shell: Compose Desktop.
- Backend: Rust engine crate in `engine/`.
- ASR: `whisper-rs` / `whisper.cpp`.
- Audio input: `cpal`.
- Clipboard: `arboard`.
- Synthetic paste: `enigo`.

Important files:
- Compose app shell: `composeApp/src/jvmMain/kotlin/fabled/quitewhisper/app/Main.kt`
- Compose MVI screen/ViewModel: `composeApp/src/commonMain/kotlin/fabled/quitewhisper/presentation/main/`
- Compose engine client: `composeApp/src/jvmMain/kotlin/fabled/quitewhisper/data/engine/ProcessEngineConnection.kt`
- Compose engine protocol/repository: `composeApp/src/commonMain/kotlin/fabled/quitewhisper/data/`
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
.\gradlew.bat :composeApp:jvmTest
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
.\gradlew.bat :composeApp:jvmTest
.\gradlew.bat :composeApp:packageDistributionForCurrentOS
```

The Compose package build produces:
- `composeApp/build/compose/binaries/main/msi/fabled.quitewhisper.compose-1.0.0.msi`
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

- Keep the MVP fabled-only. Do not introduce cloud transcription.
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
