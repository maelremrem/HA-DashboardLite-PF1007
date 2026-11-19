"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const src = fs.readFileSync(path.join(__dirname,
  "../custom_components/ha_frame/frontend/ha-frame-editor.js"), "utf8");
const registered = new Map();
const sandbox = {
  HTMLElement: class {},
  customElements: {
    define: (name, klass) => registered.set(name, klass),
    get: (name) => registered.get(name)
  },
  window: { customCards: [] },
  confirm: () => true,
  console
};

const editorApi = vm.runInNewContext(src + "\n({ nativeCss, combine, clone, esc, PRESETS, PALETTE_THEMES, LAYOUT_TEMPLATES, templatePage, withShape, withTypography, removeDesign, readShape, readTypography, gridPlacement, tileIconMarkup, mdiIconName, MDI_REGEX, MDI_PATH_REGEX, validTileStyle, isAdvancedPage, compatibleVersion })", sandbox);
assert.ok(registered.has("ha-frame-visual-editor"));
assert.equal(sandbox.window.customCards[0].type, "ha-frame-visual-editor");
assert.equal(Object.keys(editorApi.PRESETS).length, 14);
assert.equal(editorApi.esc('<script type="x">&</script>'),
  "&lt;script type=&quot;x&quot;&gt;&amp;&lt;/script&gt;");

const styles = editorApi.nativeCss(editorApi.PRESETS.ocean);
assert.equal(styles.body["background-color"], "#071c2b");
assert.equal(styles[".metric"]["border-radius"], "23px");
assert.equal(styles[".accent"].color, "#58d5e7");

const invalid = editorApi.nativeCss("body{background-image:url(https://evil.example/img)}");
assert.equal(invalid.body, undefined);

for (const [id, css] of Object.entries(editorApi.PRESETS)) {
  assert.ok(css.length <= 12000, id);
  const allowed = new Set([
    ":root", "body", ".header", ".subtitle", ".clock", ".date", ".status",
    ".settings", ".section", ".metric", ".metric-label", ".metric-value", ".control",
    ".control-label", ".control-state", ".control.on", ".control.pressed",
    ".card", ".page-tab", ".page-tab.active", ".accent"
  ]);
  const clean = css.replace(/\/\*[\s\S]*?\*\//g, "");
  let pos = 0;
  for (const m of clean.matchAll(/([^{}]+)\{([^{}]*)\}/g)) {
    assert.equal(clean.slice(pos, m.index).trim(), "", id);
    pos = m.index + m[0].length;
    for (const selector of m[1].split(",")) assert.ok(allowed.has(selector.trim()), id + " " + selector);
    for (const line of m[2].split(";").filter(x => x.trim())) {
      assert.match(line.trim(), /^(--accent|color|background-color|border-radius|font-size|font-weight):/);
    }
  }
  assert.equal(clean.slice(pos).trim(), "", id);
}
assert.equal(Object.keys(editorApi.PALETTE_THEMES).length, 10);
const changed = editorApi.withShape(editorApi.PRESETS.forest, "bubble");
assert.equal(editorApi.readShape(changed), "bubble");
assert.equal(editorApi.removeDesign(changed), editorApi.PRESETS.forest);
assert.equal(editorApi.nativeCss(changed)[".card"]["border-radius"], "36px");
assert.equal(editorApi.withShape(changed, "square").match(/Studio design/g).length, 2);
assert.equal(editorApi.nativeCss(editorApi.withShape(changed, "square"))[".card"]["border-radius"], "4px");
const large = editorApi.withTypography(editorApi.withShape(changed, "square"), "large");
assert.equal(editorApi.readTypography(large), "large");
assert.equal(editorApi.readShape(large), "square");
assert.equal(editorApi.nativeCss(large)[".clock"]["font-size"], "52px");
assert.equal(editorApi.withTypography(large, "standard").match(/Studio design/g).length, 2);
assert.equal(editorApi.withShape(editorApi.withTypography(changed, "standard"), "standard"), editorApi.PRESETS.forest);
for (const template of editorApi.LAYOUT_TEMPLATES) {
  const page = editorApi.templatePage(template);
  assert.ok([2,3,4].includes(page.columns));
  assert.ok(page.cards.length <= page.columns * 3);
  assert.ok(page.cards.every(t => t.entity_id === "" && ["metric","control","gauge","thermostat","light","cover","weather","graph","camera"].includes(t.type)));
  assert.ok(page.cards.every(t => t.min === 0 && t.max === 100));
}

const grid = editorApi.gridPlacement({columns:3,cards:[
  {col_span:2,row_span:2},{col_span:1},{col_span:1},{col_span:2},{col_span:1}
]});
assert.equal(grid.rows, 3);
assert.equal(grid.cells[0].w, 2);
assert.equal(grid.cells[1].col, 2);
assert.equal(grid.cells[3].row, 2);
assert.throws(()=>editorApi.gridPlacement({columns:2,cards:[
  {col_span:2},{col_span:2},{col_span:2},{col_span:2}
]}), /Pas assez/);
assert.equal(editorApi.mdiIconName("light"), "mdi:lightbulb");
assert.ok(editorApi.MDI_REGEX.test("mdi:home-thermometer"));
assert.ok(!editorApi.MDI_REGEX.test("<svg>"));
assert.ok(editorApi.MDI_PATH_REGEX.test("M12,2 L20,22 H4Z"));
assert.match(editorApi.tileIconMarkup("home","metric","#ffffff"), /mdi:home/);
assert.equal(editorApi.tileIconMarkup("none","metric","#ffffff"), "");
assert.match(editorApi.tileIconMarkup("","camera","#ffffff"), /mdi:camera/);
assert.ok(editorApi.validTileStyle({background:"#abcdef",radius:12}));
assert.ok(!editorApi.validTileStyle({background:"red"}));
assert.ok(editorApi.isAdvancedPage({cards:[{type:"camera"}]}));
assert.ok(!editorApi.isAdvancedPage({cards:[{type:"metric"}]}));
assert.ok(editorApi.compatibleVersion("1.0.0"));
assert.ok(!editorApi.compatibleVersion("0.9.0"));

const copied = editorApi.clone({pages:[{cards:[{label:"A"}]}]});
copied.pages[0].cards[0].label = "B";
assert.equal(editorApi.clone({pages:[{cards:[{label:"A"}]}]}).pages[0].cards[0].label,"A");

// Template actions remain in-memory until an explicit save.
const Editor = registered.get("ha-frame-visual-editor");
const instance = Object.create(Editor.prototype);
instance._config = {
  dashboard_title: "Maison", custom_css: "",
  pages: [{ title: "Origine", columns: 2, cards: [{type:"metric",label:"T",entity_id:"sensor.test",min:0,max:100}] }]
};
instance._page = 0;
instance._tile = -1;
instance._panel = "page";
instance._dirty = false;
instance._mark = () => { instance._dirty = true; };
instance._render = () => {};
instance._message = (msg) => { instance.lastMessage = msg; };

instance._applyLayout("overview", "add");
assert.equal(instance._config.pages.length, 2);
assert.equal(instance._config.pages[0].cards[0].entity_id, "sensor.test");
assert.equal(instance._config.pages[1].cards.length, 6);
assert.ok(instance._config.pages[1].cards.every(x=>x.entity_id===""));
assert.equal(instance._dirty, true);

const beforeReplace = JSON.stringify(instance._config.pages[1]);
sandbox.confirm = () => false;
instance._applyLayout("dense", "replace");
assert.equal(JSON.stringify(instance._config.pages[1]), beforeReplace);
sandbox.confirm = () => true;
instance._applyLayout("dense", "replace");
assert.equal(instance._config.pages[1].cards.length, 12);
assert.equal(instance._config.pages[1].columns, 4);

instance._config.custom_css = editorApi.PRESETS.forest;
sandbox.confirm = () => false;
instance._applyPreset("rose");
assert.equal(instance._config.custom_css, editorApi.PRESETS.forest);
sandbox.confirm = () => true;
instance._applyPreset("rose");
assert.equal(instance._config.custom_css, editorApi.PRESETS.rose);
instance._applyLayout("climate","add");
instance._applyLayout("energy","add");
instance._applyLayout("security","add");
assert.equal(instance._config.pages.length, 4);
assert.match(instance.lastMessage, /4 pages/);

const preview = {clientWidth:640, innerHTML:"", style:"", setAttribute(name,value){this[name]=value;}};
instance._hass = {states:{"weather.home":{state:"sunny",attributes:{temperature:20}}}};
instance._frames = [{entry_id:"frame-1",online:true,version:"1.0.0"}];
instance._frameId = "frame-1";
instance._page = 0;
instance._config.pages = [{title:"Caméras",columns:3,cards:[
  {type:"camera",label:"Entrée",entity_id:"camera.entree",col_span:2,row_span:2,icon:"camera"},
  {type:"weather",label:"Dehors",entity_id:"weather.home",icon:"sun"}
]}];
instance.shadowRoot = {querySelector(query){return query==="[data-preview]"?preview:null;}};
instance._preview();
assert.match(preview.innerHTML,/grid-column:1 \/ span 2/);
assert.match(preview.innerHTML,/Aperçu MJPEG/);
assert.match(preview.innerHTML,/tile-icon/);
assert.match(preview.innerHTML,/20 °C/);
assert.ok(!preview.innerHTML.includes("font-size:40px"));
instance._config.pages[0].cards[0].icon="invalid";
assert.match(instance._validate(),/Icône invalide/);
instance._config.pages[0].cards[0].icon="camera";
instance._frames[0].version="0.9.0";
assert.match(instance._validate(),/APK Android/);
instance._frames[0].version="1.0.0";
assert.equal(instance._validate(),"");

assert.match(src, /ha_frame\/editor\/list/);
assert.match(src, /ha_frame\/editor\/get/);
assert.match(src, /ha_frame\/editor\/save/);
assert.match(src, /getStubConfig/);
assert.match(src, /data-tile/);
assert.match(src, /data-control='frame'/);
assert.match(src, /_revision/);
assert.doesNotMatch(src, /localStorage|sessionStorage|eval\(/);

// Validate MDI upgrade and generated, bounded SVG paths without changing saved config.
instance._config.pages[0].cards[0].icon = "mdi:cctv";
instance._frames[0].version = "1.0.0";
assert.match(instance._validate(), /v1.1.0/);
instance._frames[0].version = "1.1.0";
assert.equal(instance._validate(), "");
instance._config.pages[0].cards[0].icon = "mdi:cctv";
instance._config.pages[0].cards[1].icon = "";
instance._resolveIconPath = async (name, stateObj) => {
  assert.ok(name === "mdi:cctv" || stateObj?.entity_id === "weather.home");
  return "M12,2L20,22H4Z";
};
instance._prepareMdiConfig().then(prepared => {
  assert.equal(prepared.pages[0].cards[0].icon, "mdi:cctv");
  assert.equal(prepared.pages[0].cards[0].icon_path, "M12,2L20,22H4Z");
  assert.equal(prepared.pages[0].cards[1].icon_path, "M12,2L20,22H4Z");
  assert.equal(instance._config.pages[0].cards[0].icon_path, undefined);
  console.log("Visual editor static, security and MDI tests passed.");
}).catch(error => { console.error(error); process.exitCode = 1; });
