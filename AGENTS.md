# QuiteWhisper Agent Notes

## Project

QuiteWhisper is a Tauri 2 desktop MVP for local push-to-talk dictation on Windows first, with macOS kept in the architecture.

Core behavior:
- Hold `Control+Alt+Space` to record.
- Release the hotkey to transcribe locally with Whisper.
- Paste the transcript into the active field through clipboard + paste shortcut.
- Restore the previous clipboard text when enabled.
- Use a small bottom-center overlay for listening/transcribing/pasted/error states.
- Use a glossary + Whisper initial prompt for technical terms. Do not add a local LLM unless explicitly requested.

## Stack

- Frontend: Vite + TypeScript + vanilla CSS.
- Desktop shell: Tauri 2.
- Backend: Rust.
- ASR: `whisper-rs` / `whisper.cpp`.
- Audio input: `cpal`.
- Clipboard: `arboard`.
- Synthetic paste: `enigo`.

Important files:
- Frontend app: `src/main.ts`
- Overlay UI: `src/overlay.ts`, `src/overlay.css`
- Tauri config: `src-tauri/tauri.conf.json`
- Backend commands: `src-tauri/src/commands.rs`
- Audio pipeline: `src-tauri/src/audio.rs`
- Whisper pipeline: `src-tauri/src/speech.rs`
- Settings: `src-tauri/src/settings.rs`
- Model download/status: `src-tauri/src/model.rs`
- Clipboard paste: `src-tauri/src/inserter.rs`
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
.\scripts\windows-dev.ps1 npm run tauri:build
```

The helper sets:
- `LIBCLANG_PATH=C:\Program Files\LLVM\bin`
- `CMAKE_GENERATOR=Ninja`
- Rust, LLVM, CMake, and Ninja paths.
- Visual Studio Developer Command Prompt environment.

For frontend-only checks:

```powershell
npm run build
```

## Verified State

The project has been verified on Windows with:

```powershell
npm run build
.\scripts\windows-dev.ps1 cargo check
.\scripts\windows-dev.ps1 cargo test
.\scripts\windows-dev.ps1 npm run tauri:build
```

The release build produced:
- `src-tauri/target/release/quite-whisper.exe`
- `src-tauri/target/release/bundle/msi/QuiteWhisper_0.1.0_x64_en-US.msi`
- `src-tauri/target/release/bundle/nsis/QuiteWhisper_0.1.0_x64-setup.exe`

A smoke launch of `quite-whisper.exe` succeeded: the process started and stayed alive for several seconds.

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

It is downloaded from Hugging Face through `src-tauri/src/model.rs`.

## Implementation Constraints

- Keep the MVP local-only. Do not introduce cloud transcription.
- Keep the LLM post-edit path as a future extension point via `TextPostProcessor`; do not add Gemma/Qwen unless requested.
- Do not replace the clipboard insertion strategy without testing against common Windows apps.
- Be careful with global shortcut changes; frontend uses Tauri global-shortcut plugin press/release events.
- The repo may not be initialized as git. Check before using git commands.
- Avoid committing build outputs from `dist/`, `target/`, or `src-tauri/target/`.

## Known Follow-Ups

- Manual test first-run model download.
- Manual test microphone capture and latency.
- Manual test paste/clipboard restoration across editors, browsers, IDEs, and messengers.
- Compare `ggml-small-q5_1.bin` latency/quality against `ggml-base-q5_1.bin` and `ggml-large-v3-turbo-q5_0.bin`.
- Add macOS permissions notes and verify macOS packaging later.
