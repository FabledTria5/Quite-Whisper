export type AppSettings = {
  hotkey: string;
  model_path: string | null;
  microphone_device_id: string | null;
  glossary_terms: string[];
  restore_clipboard: boolean;
};

export type ModelStatus = {
  configured_path: string | null;
  default_model_path: string;
  default_model_exists: boolean;
  configured_model_exists: boolean;
};

export type MicrophoneStatus = {
  default_device: string | null;
  devices: string[];
};

export type OverlayPayload = {
  state: "idle" | "listening" | "transcribing" | "pasted" | "error";
  message: string;
};
