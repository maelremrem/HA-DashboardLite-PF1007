"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const file = path.join(__dirname,
  "../custom_components/ha_frame/frontend/ha-frame-studio-panel.js");
const source = fs.readFileSync(file, "utf8");
assert.match(source, /^import "\.\/ha-frame-editor\.js\?v=1\.4\.0";/m);

const editor = {
  setConfig(value) { this.config = value; },
  set hass(value) { this.state = value; },
};
const menu = {
  hidden: true,
  addEventListener(name, cb) { if (name === "click") this.click = cb; },
};
const registered = new Map();
const calls = [];
class FakeHTMLElement {
  attachShadow() {
    this.shadowRoot = {
      innerHTML: "",
      querySelector(name) {
        if (name === "ha-frame-visual-editor") return editor;
        if (name === "[data-menu]") return menu;
        return null;
      },
    };
  }
  dispatchEvent(value) { calls.push(value); }
}
const sandbox = {
  HTMLElement: FakeHTMLElement,
  Event: class {
    constructor(type, options) { this.type = type; Object.assign(this, options); }
  },
  customElements: {
    define(name, ctor) { registered.set(name, ctor); },
    get(name) { return registered.get(name); },
  },
};
vm.runInNewContext(source.replace(/^import[^\n]*\n/m, ""), sandbox);

const Panel = registered.get("ha-frame-studio-panel");
assert.ok(Panel);
const panel = new Panel();
const hass = { states: {} };
panel.hass = hass;
panel.narrow = true;
panel.connectedCallback();
assert.equal(editor.config.type, "custom:ha-frame-visual-editor");
assert.equal(editor.state, hass);
assert.equal(menu.hidden, false);

panel.narrow = false;
assert.equal(menu.hidden, true);
panel.narrow = true;
menu.click();
assert.equal(calls[0].type, "hass-toggle-menu");
assert.equal(calls[0].bubbles, true);
assert.equal(calls[0].composed, true);

const nextHass = { states: { "sensor.test": {} } };
panel.hass = nextHass;
assert.equal(editor.state, nextHass);
panel.connectedCallback();
assert.equal(editor.state, nextHass);
console.log("Standalone HA Frame Studio panel checks passed.");
