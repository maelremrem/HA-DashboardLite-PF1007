/* HA Frame Studio full-page Home Assistant panel. */
import "./ha-frame-editor.js?v=1.4.0";

class HaFrameStudioPanel extends HTMLElement {
  constructor() {
    super();
    this.attachShadow({ mode: "open" });
    this._hass = null;
    this._narrow = false;
    this._editor = null;
  }

  set hass(value) {
    this._hass = value;
    if (this._editor) this._editor.hass = value;
  }

  set narrow(value) {
    this._narrow = Boolean(value);
    this._updateNavigation();
  }

  set panel(value) {
    this._panel = value;
  }

  connectedCallback() {
    if (this._editor) return;
    this.shadowRoot.innerHTML = `
      <style>
        :host {
          display: block;
          min-height: 100%;
          background: var(--primary-background-color, #f5f7fa);
          color: var(--primary-text-color, #223047);
          box-sizing: border-box;
        }
        .header {
          display: flex;
          align-items: center;
          gap: 14px;
          padding: 18px 24px;
          background: var(--card-background-color, #fff);
          border-bottom: 1px solid var(--divider-color, #ddd);
        }
        .menu {
          display: none;
          border: 0;
          background: transparent;
          color: inherit;
          cursor: pointer;
          padding: 8px;
          border-radius: 8px;
          font-size: 22px;
        }
        .menu[hidden] { display: none; }
        .menu:not([hidden]) { display: inline-flex; }
        h1 { font-size: 20px; line-height: 1.3; margin: 0; font-weight: 700; }
        p { margin: 3px 0 0; font-size: 13px; color: var(--secondary-text-color, #667085); }
        .workspace {
          padding: 24px;
          box-sizing: border-box;
          max-width: 1680px;
          margin: 0 auto;
        }
        ha-frame-visual-editor { display: block; width: 100%; }
        @media (max-width: 700px) {
          .header { padding: 12px; }
          .workspace { padding: 10px; }
          h1 { font-size: 17px; }
          p { font-size: 12px; }
        }
      </style>
      <div class="header">
        <button class="menu" type="button" aria-label="Ouvrir le menu Home Assistant" data-menu hidden>☰</button>
        <div>
          <h1>HA Frame Studio</h1>
          <p>Personnalisation des cadres, pages, tuiles et thèmes</p>
        </div>
      </div>
      <main class="workspace">
        <ha-frame-visual-editor></ha-frame-visual-editor>
      </main>
    `;
    this.shadowRoot.querySelector("[data-menu]").addEventListener("click", () => {
      this.dispatchEvent(new Event("hass-toggle-menu", { bubbles: true, composed: true }));
    });
    this._editor = this.shadowRoot.querySelector("ha-frame-visual-editor");
    this._editor.setConfig({ type: "custom:ha-frame-visual-editor" });
    if (this._hass) this._editor.hass = this._hass;
    this._updateNavigation();
  }

  _updateNavigation() {
    const button = this.shadowRoot.querySelector("[data-menu]");
    if (button) button.hidden = !this._narrow;
  }
}

if (!customElements.get("ha-frame-studio-panel")) {
  customElements.define("ha-frame-studio-panel", HaFrameStudioPanel);
}
