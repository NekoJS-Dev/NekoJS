const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const crypto = require('node:crypto');
const repository = path.resolve(__dirname, '../../../..');
const typescript = require(path.join(repository, 'node_modules/typescript'));
const runtimePath = path.join(repository, 'common/src/main/resources/nekojs/node/modules/jsx-runtime.ts');
const runtimeSource = fs.readFileSync(runtimePath, 'utf8');
const compiled = typescript.transpileModule(runtimeSource, {
  compilerOptions: { target: typescript.ScriptTarget.ES2022, module: typescript.ModuleKind.None }
}).outputText;
let exported;
const sandbox = { __nekoNodeDefine: (names, module) => { exported = module; } };
vm.createContext(sandbox);
vm.runInContext(compiled, sandbox, { filename: 'jsx-runtime.transpiled.js' });
sandbox.record = value => {
  process.stdout.write(JSON.stringify(value, null, 2) + '\n');
};
sandbox.check = (value, message) => assert.equal(value, true, message);
sandbox.api = exported.UI;
vm.runInContext(`
const inputs = [
  { width: 100, height: 100 }, { width: 320, height: 180 },
  { width: 480, height: 240 }, { width: 640, height: 360 },
  { width: 854, height: 480 }, { width: 1280, height: 720 }
];
let renders = 0, commits = 0, failCommit = false, targetWidth = 40;
let latestLayoutCandidate = null;
const rawCreated = [];
const host = {
  isOwnerThread: () => true, enqueue: () => false,
  supportsPrimitive: () => true,
  measureText: (text, size, width) => ({ width: 0, height: 0 }),
  layout: (tree, viewport, snapshot) => { latestLayoutCandidate = snapshot; },
  reportDiagnostic: diagnostic => {},
  begin: () => ({
    create: (type, key, props) => { rawCreated.push({type, props}); return {}; },
    update: () => {}, order: () => {}, remove: () => {}, rollback: () => {},
    commit: () => { if (failCommit) throw new Error('injected commit failure'); commits++; }
  })
};
const render = () => {
  renders++;
  return api.element('screen', { id: 'screen', children: [
    api.element('row', { id: 'row', width: 200, height: 28, justify: 'end',
      children: api.element('button', { id: 'target', width: targetWidth, height: 20, text: 'HIT' }) }),
    api.element('panel', { id: 'responsive', width: {base: 80, profiles: {4: 120, 6: 160}}, height: 24 })
  ]});
};
const root = api.createRoot(render, host, { id: 'parity', viewport: inputs[0] });
const before = { renders, commits };
const measurements = inputs.map((input, index) => {
  if (index > 0) root.resize(input);
  const snapshot = root.layout();
  return { profile: snapshot.profile, target: snapshot.nodes[0].children[0].children[0].rect,
    responsiveWidth: snapshot.nodes[0].children[1].rect.width };
});
check(measurements.map(row => row.profile).join(',') === '1,2,3,4,5,6', 'all profiles measured');
check(measurements.map(row => row.responsiveWidth).join(',') === '80,80,80,120,120,160', 'profile fallback measured');
check(measurements.every(row => row.target.x === 160), 'justify end is measured');
check(before.renders === renders && before.commits === commits, 'resize does not render or transact');
const rawPanel = rawCreated.find(row => row.props.id === 'responsive');
check(typeof rawPanel.props.width === 'object', 'responsive raw props cross transaction boundary unchanged');
record({
  engine: 'Node + installed TypeScript transpiler; real common runtime, fake transaction capture',
  measurements, renders, commits, resizeTriggeredCommits: commits - before.commits,
  rawResponsiveWidthAtCreate: rawPanel.props.width,
  hostProjectionFromInspectedJavaSource: {
    targetX: 0, responsiveWidth: 100,
    status: 'source-derived projection, not an executed Minecraft Adapter'
  }
});
failCommit = true;
targetWidth = 80;
check(root.refresh() === false, 'failed commit is rejected');
const activeTarget = root.layout().nodes[0].children[0].children[0].rect;
const offeredTarget = latestLayoutCandidate.nodes[0].children[0].children[0].rect;
check(activeTarget.width === 40 && offeredTarget.width === 80, 'failed candidate differs from active measurements');
record({
  injectedFailure: 'transaction commit',
  commonActiveTarget: activeTarget,
  previouslyOfferedLayoutCandidate: offeredTarget,
  consequence: 'An adapter publishing inspect() directly in layout() would expose rejected geometry'
});
failCommit = false;
root.close();
`, sandbox, { filename: 'ticket43-public-layout-repro.js' });
process.stdout.write(JSON.stringify({
  runtimeSha256: crypto.createHash('sha256').update(runtimeSource).digest('hex'),
  goldenSha256: crypto.createHash('sha256').update(fs.readFileSync(path.join(repository,
    'common/src/test/resources/nekojs/inspector/six-profiles-golden.txt'))).digest('hex')
}, null, 2) + '\n');
