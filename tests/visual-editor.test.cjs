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
  console
};

const exports = vm.runInNewContext(src + "\n({ nativeCss, combine, clone, esc, PRESETS })", sandbox);
assert.ok(registered.has("ha-frame-visual-editor"));
assert.equal(sandbox.window.customCards[0].type, "ha-frame-visual-editor");
assert.equal(Object.keys(exports.PRESETS).length, 4);
assert.equal(exports.esc('<script type="x">&</script>'),
  "&lt;script type=&quot;x&quot;&gt;&amp;&lt;/script&gt;");

const styles = exports.nativeCss(exports.PRESETS.ocean);
assert.equal(styles.body["background-color"], "#071c2b");
assert.equal(styles[".metric"]["border-radius"], "23px");
assert.equal(styles[".accent"].color, "#58d5e7");

const invalid = exports.nativeCss("body{background-image:url(https://evil.example/img)}");
assert.equal(invalid.body, undefined);

const copied = exports.clone({pages:[{cards:[{label:"A"}]}]});
copied.pages[0].cards[0].label = "B";
assert.equal(exports.clone({pages:[{cards:[{label:"A"}]}]}).pages[0].cards[0].label,"A");

assert.match(src, /ha_frame\/editor\/list/);
assert.match(src, /ha_frame\/editor\/get/);
assert.match(src, /ha_frame\/editor\/save/);
assert.match(src, /getStubConfig/);
assert.match(src, /data-tile/);
assert.match(src, /data-control='frame'/);
assert.match(src, /_revision/);
assert.doesNotMatch(src, /localStorage|sessionStorage|eval\(/);

console.log("Visual editor static, security and theme tests passed.");
