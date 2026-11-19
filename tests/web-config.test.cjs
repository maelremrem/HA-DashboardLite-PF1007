const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

const filename = path.join(__dirname, '..', 'app', 'src', 'main', 'assets', 'config', 'app.js');
const script = fs.readFileSync(filename, 'utf8');
const context = vm.createContext({});
const start = script.indexOf('function reorderSlots(');
const end = script.indexOf('\nfunction moveSlot(', start);
assert.ok(start >= 0 && end > start);
vm.runInContext(script.slice(start, end), context);

const slots = Array.from({ length: 9 }, (_, id) => ({ label: `Slot ${id}`, entity_id: `sensor.s${id}` }));
assert.equal(vm.runInContext('typeof reorderSlots', context), 'function');
context.slotsToTest = slots;
assert.equal(vm.runInContext('reorderSlots(slotsToTest, 0, -1)', context), false);
assert.equal(vm.runInContext('reorderSlots(slotsToTest, 2, 1)', context), false);
assert.equal(vm.runInContext('reorderSlots(slotsToTest, 3, -1)', context), false);
assert.equal(vm.runInContext('reorderSlots(slotsToTest, 8, 1)', context), false);
assert.equal(vm.runInContext('reorderSlots(slotsToTest, 1, 1)', context), true);
assert.equal(slots[1].label, 'Slot 2');
assert.equal(slots[2].entity_id, 'sensor.s1');
assert.equal(vm.runInContext('reorderSlots(slotsToTest, 4, -1)', context), true);
assert.equal(slots[3].label, 'Slot 4');
assert.equal(slots[4].label, 'Slot 3');
console.log('Web configuration reorder tests passed');
