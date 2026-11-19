const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const file = fs.readFileSync(path.join(__dirname, '../app/src/main/assets/config/app.js'), 'utf8');
const start = file.indexOf('const CSS_PRESETS = ');
const end = file.indexOf('\nasync function request(', start);
assert.ok(start !== -1 && end !== -1);
const context = vm.createContext({});
vm.runInContext(file.slice(start, end).replace('const CSS_PRESETS', 'var CSS_PRESETS'), context);
assert.equal(Object.keys(context.CSS_PRESETS).length, 4);
const out = process.argv[2];
if (out) fs.writeFileSync(out, JSON.stringify(context.CSS_PRESETS, null, 2));
for (const [key, css] of Object.entries(context.CSS_PRESETS)) {
  assert.equal(typeof css, 'string');
  assert.ok(css.length <= 12000, key);
}
console.log('CSS preset tests passed');
