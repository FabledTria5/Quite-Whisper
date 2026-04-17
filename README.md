# QuiteWhisper

Local push-to-talk dictation for Windows and macOS.

The MVP records while `Control+Alt+Space` is held, transcribes locally with Whisper, and pastes the result into the active field. It uses a Tauri 2 UI, a Rust backend, microphone capture through `cpal`, and `whisper.cpp` through Rust bindings.

## Requirements

- Node.js 20 or newer.
- Rust stable with Cargo.
- Windows: Microsoft C++ Build Tools.
- macOS: Xcode Command Line Tools and microphone/accessibility permissions.

## Development

```powershell
npm install
npm run tauri:dev
```

On Windows, run Rust/Tauri commands through the helper so MSVC, LLVM/libclang, CMake, and Ninja are visible in the same shell:

```powershell
.\scripts\windows-dev.ps1 cargo check
.\scripts\windows-dev.ps1 cargo test
.\scripts\windows-dev.ps1 npm run tauri:build
```

The first run can download `ggml-small-q5_1.bin` into the app config directory. You can also select an existing `.bin` model in the settings window, including a heavier model such as `ggml-large-v3-turbo-q5_0.bin` when accuracy matters more than latency.

## Current MVP Behavior

- Hold `Control+Alt+Space` to record.
- Release the hotkey to transcribe.
- A small bottom-center overlay shows listening, transcribing, pasted, and error states.
- The app saves current clipboard text, pastes the transcript, then restores the previous clipboard text when enabled.
- Glossary terms are added to the Whisper initial prompt to help with technical words.

## Notes

- The app does not use a local LLM in v1. `TextPostProcessor` is present as the extension point for future Gemma/Qwen-style correction.
- Rust is not vendored. Install it from `https://rustup.rs/` before building.
- Windows builds need Visual Studio Build Tools with MSVC, LLVM, CMake, and Ninja.
- The default model is the faster `small-q5_1` preset. If accuracy is not enough for technical dictation, select a heavier model manually and compare latency.
