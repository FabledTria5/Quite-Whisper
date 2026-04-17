import { listen } from "@tauri-apps/api/event";
import "./overlay.css";
import type { OverlayPayload } from "./types";

const root = requireElement<HTMLDivElement>("#overlay");

function render(payload: OverlayPayload) {
  root.innerHTML = `
    <section class="pill ${payload.state}">
      <span class="dot"></span>
      <span>${payload.message}</span>
    </section>
  `;
}

render({ state: "idle", message: "QuiteWhisper" });

listen<OverlayPayload>("overlay_status", (event) => {
  render(event.payload);
});

function requireElement<T extends Element>(selector: string): T {
  const element = document.querySelector<T>(selector);
  if (!element) {
    throw new Error(`Missing ${selector} root`);
  }
  return element;
}
