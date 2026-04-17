import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import { register, unregisterAll } from "@tauri-apps/plugin-global-shortcut";
import "./styles.css";
import type { AppSettings, MicrophoneStatus, ModelStatus } from "./types";

const app = requireElement<HTMLDivElement>("#app");

let settings: AppSettings | null = null;
let recording = false;

const state = {
  status: "Loading settings...",
  shortcutStatus: "",
  modelStatus: null as ModelStatus | null,
  microphoneStatus: null as MicrophoneStatus | null,
};

function render() {
  if (!settings) {
    app.innerHTML = `<section class="page"><p>${state.status}</p></section>`;
    return;
  }

  const glossary = settings.glossary_terms.join("\n");
  const modelPath = settings.model_path ?? "";
  const defaultModelText = state.modelStatus?.default_model_exists
    ? "Default model is downloaded."
    : "Default model is not downloaded yet.";
  const configuredModelText = settings.model_path
    ? state.modelStatus?.configured_model_exists
      ? "Selected model exists."
      : "Selected model was not found."
    : "Default model will be used.";

  app.innerHTML = `
    <section class="page">
      <header class="header">
        <div>
          <p class="eyebrow">Local dictation</p>
          <h1>QuiteWhisper</h1>
        </div>
        <span class="status">${state.status}</span>
      </header>

      <section class="panel">
        <h2>Recording</h2>
        <label>
          Push-to-talk hotkey
          <input id="hotkey" value="${escapeHtml(settings.hotkey)}" spellcheck="false" />
        </label>
        <p class="hint">${state.shortcutStatus}</p>
        <button id="save">Save settings</button>
      </section>

      <section class="panel">
        <h2>Speech model</h2>
        <label>
          Model path
          <input id="modelPath" value="${escapeHtml(modelPath)}" spellcheck="false" placeholder="Use the default downloaded model" />
        </label>
        <div class="button-row">
          <button id="selectModel">Choose model</button>
          <button id="downloadModel">Download default model</button>
        </div>
        <p class="hint">${defaultModelText} ${configuredModelText}</p>
        <p class="path">${escapeHtml(state.modelStatus?.default_model_path ?? "")}</p>
      </section>

      <section class="panel">
        <h2>Microphone</h2>
        <button id="testMic">Check microphone</button>
        <p class="hint">Default: ${escapeHtml(state.microphoneStatus?.default_device ?? "not checked")}</p>
      </section>

      <section class="panel">
        <h2>Technical glossary</h2>
        <textarea id="glossary" rows="8" spellcheck="false" placeholder="Kotlin&#10;Jetpack Compose&#10;Gradle&#10;CTranslate2">${escapeHtml(glossary)}</textarea>
        <label class="checkbox">
          <input id="restoreClipboard" type="checkbox" ${settings.restore_clipboard ? "checked" : ""} />
          Restore clipboard after paste
        </label>
      </section>
    </section>
  `;

  bindControls();
}

function bindControls() {
  document.querySelector("#save")?.addEventListener("click", saveSettings);
  document.querySelector("#downloadModel")?.addEventListener("click", downloadDefaultModel);
  document.querySelector("#selectModel")?.addEventListener("click", selectModel);
  document.querySelector("#testMic")?.addEventListener("click", checkMicrophone);
}

async function saveSettings() {
  if (!settings) return;

  const hotkey = valueOf("#hotkey") || "Control+Alt+Space";
  const modelPath = valueOf("#modelPath");
  const glossaryTerms = valueOf("#glossary")
    .split(/\r?\n/)
    .map((item) => item.trim())
    .filter(Boolean);
  const restoreClipboard = Boolean(document.querySelector<HTMLInputElement>("#restoreClipboard")?.checked);

  await persistSettings({
    ...settings,
    hotkey,
    model_path: modelPath.length > 0 ? modelPath : null,
    glossary_terms: glossaryTerms,
    restore_clipboard: restoreClipboard,
  });

  state.status = "Settings saved.";
  render();
}

async function downloadDefaultModel() {
  state.status = "Downloading default model...";
  render();
  try {
    state.modelStatus = await invoke<ModelStatus>("download_default_model");
    state.status = "Default model downloaded.";
  } catch (error) {
    state.status = errorMessage(error);
  }
  render();
}

async function selectModel() {
  try {
    const selected = await invoke<string | null>("select_model_path");
    if (selected && settings) {
      await persistSettings({
        ...settings,
        model_path: selected,
      });
      state.status = "Model selected.";
      render();
    }
  } catch (error) {
    state.status = errorMessage(error);
    render();
  }
}

async function checkMicrophone() {
  try {
    state.microphoneStatus = await invoke<MicrophoneStatus>("test_microphone");
    state.status = "Microphone check complete.";
  } catch (error) {
    state.status = errorMessage(error);
  }
  render();
}

async function registerShortcut(hotkey: string) {
  await unregisterAll();
  await register(hotkey, async (event) => {
    if (event.state === "Pressed" && !recording) {
      recording = true;
      await invoke("start_recording");
    }

    if (event.state === "Released" && recording) {
      recording = false;
      await invoke("stop_recording_and_transcribe");
    }
  });
  state.shortcutStatus = `Hold ${hotkey} to dictate.`;
}

async function persistSettings(nextSettings: AppSettings) {
  settings = await invoke<AppSettings>("save_settings", {
    settings: nextSettings,
  });
  await registerShortcut(settings.hotkey);
  await refreshModelStatus();
}

async function refreshModelStatus() {
  state.modelStatus = await invoke<ModelStatus>("get_model_status");
}

function valueOf(selector: string): string {
  return document.querySelector<HTMLInputElement | HTMLTextAreaElement>(selector)?.value.trim() ?? "";
}

function escapeHtml(value: string): string {
  return value.replace(/[&<>"']/g, (char) => {
    const replacements: Record<string, string> = {
      "&": "&amp;",
      "<": "&lt;",
      ">": "&gt;",
      '"': "&quot;",
      "'": "&#039;",
    };
    return replacements[char] ?? char;
  });
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

function requireElement<T extends Element>(selector: string): T {
  const element = document.querySelector<T>(selector);
  if (!element) {
    throw new Error(`Missing ${selector} root`);
  }
  return element;
}

async function boot() {
  await listen("transcription_done", () => {
    state.status = "Text pasted.";
    render();
  });

  await listen<string>("transcription_failed", (event) => {
    state.status = event.payload;
    render();
  });

  settings = await invoke<AppSettings>("get_settings");
  await refreshModelStatus();
  await registerShortcut(settings.hotkey);
  state.status = "Ready.";
  render();
}

boot().catch((error) => {
  state.status = errorMessage(error);
  render();
});
