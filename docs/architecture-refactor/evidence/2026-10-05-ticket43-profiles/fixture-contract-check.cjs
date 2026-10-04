const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const repository = path.resolve(__dirname, '../../../..');
const typescript = require(path.join(repository, 'node_modules/typescript'));
const runtimePath = path.join(repository, 'common/src/main/resources/nekojs/node/modules/jsx-runtime.ts');
const fixturePath = path.join(repository, 'src/test/resources/nekojs/client/ui/ticket43-profiles-flow.tsx');
const transpile = source => typescript.transpileModule(source, {
  compilerOptions: { target: typescript.ScriptTarget.ES2022, module: typescript.ModuleKind.CommonJS }
}).outputText;
const messages = [];
let runtimeExports;
const context = vm.createContext({
  __nekoNodeDefine: (names, exported) => { runtimeExports = exported; },
  console: { info: message => messages.push(message) }
});
vm.runInContext(transpile(fs.readFileSync(runtimePath, 'utf8')), context);
vm.runInContext(`
const ticks = [];
const hosts = [];
const ClientEvents = { tickPost: callback => ticks.push(callback) };
function javaList(values) {
  return { size: () => values.length, get: index => values[index] };
}
function recordObject(value) {
  const record = {};
  for (const key of Object.keys(value)) record[key] = () => value[key];
  return record;
}
function inspectorNode(node) {
  return recordObject({
    id: node.id, type: node.type, visible: node.visible,
    rect: recordObject(node.rect), clip: recordObject(node.clip),
    style: new Map(Object.entries(node.style)),
    children: javaList(node.children.map(inspectorNode))
  });
}
const ClientUI = { screen: () => {
  let snapshot = null, rootHandle = null;
  const host = {
    isOwnerThread: () => true, enqueue: () => false, supportsPrimitive: () => true,
    measureText: (text, size, maxWidth) => ({ width: Math.min(maxWidth, String(text).length * 5), height: 9 }),
    viewport: () => new Map([['width', 854], ['height', 480]]),
    layout: (tree, viewport, nextSnapshot) => { snapshot = nextSnapshot; },
    reportDiagnostic: diagnostic => { throw diagnostic.error; },
    begin: () => ({create: () => ({}), update: () => {}, order: () => {}, remove: () => {}, commit: () => {}, rollback: () => {}}),
    bindRoot: handle => { rootHandle = handle; },
    open: () => rootHandle.resize(host.viewport()),
    inspect: () => recordObject({ nodes: javaList(snapshot.nodes.map(inspectorNode)), profile: snapshot.profile })
  };
  hosts.push(host);
  return host;
}};
`, context);
const fixtureModule = { exports: {} };
context.fixtureExports = fixtureModule.exports;
context.fixtureRequire = name => {
  assert.equal(name, 'nekojs/jsx-runtime');
  return runtimeExports;
};
vm.runInContext('(function(exports, require) {\n' + transpile(fs.readFileSync(fixturePath, 'utf8'))
  + '\n})(fixtureExports, fixtureRequire);', context);
vm.runInContext('ticks.forEach(callback => callback()); ticks.forEach(callback => callback());', context);
const measurements = messages.filter(message => message.startsWith('[ticket43] measurement '))
  .map(message => JSON.parse(message.slice('[ticket43] measurement '.length)));
const profiles = measurements.filter(measurement => measurement.label.startsWith('explicit-profile-'));
assert.equal(profiles.map(measurement => measurement.profile).join(','), '1,2,3,4,5,6');
assert.equal(measurements.length, 8);
assert.equal(profiles.every(measurement => measurement.inspectorMatchesCommon), true);
assert.equal(profiles.every(measurement => measurement.renderCount === 1), true);
fixtureModule.exports.root.dispatch('next-profile', 'click', {});
fixtureModule.exports.root.dispatch('parity-target', 'click', { x: 188, y: 46 });
assert.equal(messages.some(message => message.startsWith('[ticket43] target-hit ')), true);
fixtureModule.exports.root.close();
process.stdout.write(JSON.stringify({
  result: 'PASS', environment: 'Node + TypeScript, fake host with Java-shaped Inspector records',
  fixtureMeasurements: measurements.length, explicitProfiles: profiles.map(measurement => measurement.profile),
  resizeRenderCounts: profiles.map(measurement => measurement.renderCount),
  targetCenters: profiles.map(measurement => measurement.targetCenter),
  status: 'Fixture logic/record serialization executed; actual Minecraft rendering/hit testing NOT RUN'
}, null, 2) + '\n');
