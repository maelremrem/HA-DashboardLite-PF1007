"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");
const path = require("node:path");

const source = fs.readFileSync(path.join(__dirname,
  "../custom_components/ha_frame/frontend/ha-frame-editor.js"), "utf8");

const registered = new Map();
const sandbox = {
  HTMLElement: class {},
  customElements: {define:(name,type)=>registered.set(name,type),
    get:name=>registered.get(name)},
  window: {customCards: []},
  confirm: () => true,
  console
};
const api = vm.runInNewContext(source + "\n({TYPES,RULE_OPERATORS,checkRule,matchesRule,supportsAdvancedApk})",sandbox);
assert.ok(registered.has("ha-frame-visual-editor"));
for(const type of ["calendar","todo","bambu","energy","air_quality","presence",
  "circular_gauge","history"]) {
  assert.ok(api.TYPES.some(([id])=>id===type),type);
}
assert.ok(api.supportsAdvancedApk("2.1.0"));
assert.ok(api.supportsAdvancedApk("1.2.0"));
assert.equal(api.supportsAdvancedApk("1.1.1"),false);
const state={ "sensor.temperature": {state:"29",attributes:{}} };
assert.equal(api.checkRule({entity_id:"sensor.temperature",operator:"above",value:"25"}),"");
assert.equal(api.matchesRule({entity_id:"sensor.temperature",operator:"above",value:"25"},state),true);
assert.equal(api.matchesRule({entity_id:"sensor.temperature",operator:"below",value:"25"},state),false);
assert.ok(api.checkRule({entity_id:"sensor.temperature",operator:"__proto__",value:"25"}));
assert.ok(api.checkRule({entity_id:"sensor.temperature",operator:"above",value:"alert(1)"}));
assert.ok(api.checkRule({entity_id:"sensor.temperature",operator:"above",value:"25",background:"url(foo)"}));
assert.ok(api.checkRule({entity_id:"sensor.temperature",operator:"above",value:"25",hide:"true"}));
assert.equal(api.checkRule({entity_id:"",operator:"time_between",value:"22:00-07:00"}),"");
assert.ok(api.checkRule({entity_id:"",operator:"time_between",value:"98:00-07:00"}));

for(const name of ["ha_frame/editor/profiles","ha_frame/editor/profile_get",
  "ha_frame/editor/profile_save","ha_frame/editor/snapshots",
  "ha_frame/editor/diagnostics","ha_frame/editor/screenshot",
  "ha_frame/editor/logs","ha_frame/editor/reload"]) {
  assert.ok(source.includes(name),name);
}
for(const binding of ["page-auto-entity","rule-entity","photo-source","history-hours",
  "tap-action","long-press","data-action='capture'","data-action='export-layout'"]) {
  assert.ok(source.includes(binding),binding);
}
console.log("Roadmap Studio types, rules, settings and secure endpoints validated.");
