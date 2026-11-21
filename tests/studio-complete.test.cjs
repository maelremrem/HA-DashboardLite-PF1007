"use strict";

const assert=require("node:assert/strict");
const fs=require("node:fs");
const vm=require("node:vm");
const path=require("node:path");

const source=fs.readFileSync(path.join(__dirname,
  "../custom_components/ha_frame/frontend/ha-frame-editor.js"),"utf8");
const elements=new Map();
const scope={
  HTMLElement:class {},
  customElements:{define:(name,value)=>elements.set(name,value),get:name=>elements.get(name)},
  window:{customCards:[]},
  confirm:()=>true,
  console
};
const helpers=vm.runInNewContext(source+
  "\n({checkRule,matchesRule,MDI_REGEX,HaFrameVisualEditor})",scope);

const condition={
  entity_id:"sensor.temperature",operator:"above",value:"24",
  icon:"mdi:thermometer-alert",icon_path:"M12,2L20,22H4Z",
  background:"#f29c24",hide:false
};
assert.equal(helpers.checkRule(condition),"");
assert.equal(helpers.matchesRule(condition,{
  "sensor.temperature":{state:"25",attributes:{}}
}),true);
for(const invalid of [
  {...condition,icon:"javascript:alert(1)"},
  {...condition,icon_path:"<svg onload=alert(1)>"},
  {...condition,icon_path:"M"+"2".repeat(8500)}
]) {
  assert.notEqual(helpers.checkRule(invalid),"");
}
for(const feature of [
  "ha_frame/editor/photo_upload","ha_frame/editor/media_roots",
  "ha_frame/editor/media_browse","ha_frame/editor/media_sync",
  "ha_frame/editor/apk_begin","ha_frame/editor/apk_chunk",
  "ha_frame/editor/apk_finish",
  "data-photo-import","data-apk-file","data-media-entry",
  "data-bind='rule-icon'","data-bind='photo-quiet-start'",
  "data-bind='photo-transition'","data-action='media-sync'"
])assert.ok(source.includes(feature),feature);

assert.ok(elements.has("ha-frame-visual-editor"));
console.log("Studio 2.2 conditional icons, media sync, photos and APK UI checks passed.");
