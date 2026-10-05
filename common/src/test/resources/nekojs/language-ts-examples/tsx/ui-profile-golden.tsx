import { UI } from 'nekojs/jsx-runtime';

const profileInputs = [
  { width: 100, height: 100 },
  { width: 320, height: 180 },
  { width: 480, height: 240 },
  { width: 640, height: 360 },
  { width: 854, height: 480 },
  { width: 1280, height: 720 }
];

function makeHost() {
  const counts = { layouts: 0, commits: 0, creates: 0 };
  const diagnostics = [];
  const offered = [];
  let roots = [];
  return {
    isOwnerThread: () => true,
    enqueue: () => false,
    supportsPrimitive: type => UI.primitives().includes(type),
    measureText: () => ({ width: 0, height: 0 }),
    layout: (tree, viewport, snapshot, publish) => {
      counts.layouts++;
      offered.push({ snapshot, publish: publish === true });
    },
    reportDiagnostic: diagnostic => diagnostics.push({
      phase: diagnostic.phase,
      rootId: diagnostic.rootId,
      message: String(diagnostic.error.message)
    }),
    begin: () => ({
      create: () => ++counts.creates,
      update: () => { },
      order: () => { },
      remove: () => { },
      commit: nextRoots => { counts.commits++; roots = nextRoots.slice(); },
      rollback: () => { }
    }),
    counts: () => ({ ...counts }),
    roots: () => roots.slice(),
    offered: () => offered.slice(),
    diagnostics: () => diagnostics.slice()
  };
}

export function invalidProof() {
  const records = [];
  const invalidProps = [
    ['profile-zero', { width: { base: 10, profiles: { 0: 20 } } }],
    ['profile-seven', { width: { base: 10, profiles: { 7: 20 } } }],
    ['profile-fraction', { width: { profiles: { '2.5': 20 } } }],
    ['profile-text', { width: { profiles: { wide: 20 } } }],
    ['profile-map-array', { width: { profiles: [20] } }],
    ['negative-percent', { width: '-25%' }],
    ['malformed-percent', { width: '25%%' }],
    ['negative-width', { width: -1 }],
    ['nan-width', { width: NaN }],
    ['infinite-width', { width: Infinity }],
    ['negative-minimum', { minWidth: -1 }],
    ['inverted-limits', { minWidth: 20, maxWidth: 10 }],
    ['inverted-height-limits', { minHeight: 20, maxHeight: 10 }]
  ];
  for (const [name, props] of invalidProps) {
    const host = makeHost();
    let message = null;
    try {
      const root = UI.createRoot(() => UI.element('spacer', props), host,
        { id: 'invalid-' + name, viewport: profileInputs[1] });
      root.close();
    } catch (error) { message = error.message; }
    records.push({ name, boundary: 'createRoot', message, counts: host.counts(), diagnostics: host.diagnostics() });
  }
  const invalidViewports = [
    ['zero-width', { width: 0, height: 100 }],
    ['negative-height', { width: 100, height: -1 }],
    ['infinite-ratio', { width: Infinity, height: 100 }],
    ['nan-ratio', { width: 100, height: NaN }],
    ['zero-gui-scale', { width: 100, height: 100, guiScale: 0 }],
    ['negative-safe-area', { width: 100, height: 100, safeArea: { left: -1 } }],
    ['empty-content', { width: 100, height: 100, safeArea: { left: 50, right: 50 } }],
    ['profile-cap-zero', { width: 100, height: 100, capabilities: { maxProfile: 0 } }],
    ['profile-cap-seven', { width: 100, height: 100, capabilities: { maxProfile: 7 } }],
    ['profile-cap-fraction', { width: 100, height: 100, capabilities: { maxProfile: 2.5 } }],
    ['missing-design-height', { width: 100, height: 100, designWidth: 100 }],
    ['zero-design-width', { width: 100, height: 100, designWidth: 0, designHeight: 100 }],
    ['infinite-design-height', { width: 100, height: 100, designWidth: 100, designHeight: Infinity }]
  ];
  for (const [viewportName, invalidViewport] of invalidViewports) {
    let message = null;
    try { UI.profileFor(invalidViewport); } catch (error) { message = error.message; }
    records.push({ name: viewportName, boundary: 'profileFor', message });
  }
  return records;
}

export function rejectedResizeProof() {
  const host = makeHost();
  let renders = 0;
  const root = UI.createRoot(() => {
    renders++;
    return <spacer id="resize-target" width={40} height={10}
      minWidth={{ base: 10, profiles: { 4: 30 } }} maxWidth={{ base: 100, profiles: { 4: 20 } }} />;
  }, host, { id: 'ticket43-rejected-resize', viewport: profileInputs[0] });
  const before = root.layout();
  const beforeRoots = host.roots();
  const rejected = root.resize(profileInputs[3]);
  const afterRejection = root.layout();
  let invalidMessage = null;
  try { root.resize({ width: 0, height: 100 }); } catch (error) { invalidMessage = error.message; }
  const afterInvalid = root.layout();
  const recovered = root.resize(profileInputs[1]);
  const result = {
    before, afterRejection, afterInvalid, recoveredSnapshot: root.layout(),
    beforeRoots, afterRoots: host.roots(), rejected, recovered, invalidMessage,
    renders, counts: host.counts(), diagnostics: host.diagnostics()
  };
  root.close();
  return result;
}

export function profileProof() {
  const records = profileInputs.map((viewport, index) => ({
    name: 'boundary-' + (index + 1), input: viewport, profile: UI.profileFor(viewport)
  }));
  const extras = [
    ['width-below-boundary', { width: 319, height: 180 }],
    ['height-below-boundary', { width: 320, height: 179 }],
    ['lower-axis', { width: 1280, height: 240 }],
    ['safe-area-tier', { width: 500, height: 260, safeArea: { left: 10, right: 11, top: 10, bottom: 10 } }],
    ['capability-cap', { width: 1280, height: 720, capabilities: { maxProfile: 3 } }],
    ['gui-scale-independent', { width: 320, height: 180, guiScale: 6 }],
    ['design-ratio-independent', { width: 320, height: 180, designWidth: 160, designHeight: 90 }]
  ];
  for (const [name, viewport] of extras) records.push({ name, input: viewport, profile: UI.profileFor(viewport) });
  return records;
}

export function geometryProof() {
  const host = makeHost();
  const root = UI.createRoot(() => <stack id="geometry-root" width="fill" height="fill">
    <spacer id="percent-clamp" width={{ base: '50%', profiles: { 4: '25%' } }}
      height="50%" minWidth={{ base: 80, profiles: { 4: 100 } }}
      maxWidth={{ base: 200, profiles: { 4: 300 } }} minHeight={40} maxHeight={120} />
    <spacer id="percent-limits" width={10} height={10} minWidth="25%" maxWidth="50%"
      minHeight="25%" maxHeight="50%" />
    <spacer id="percent-overflow" width="120%" height={10} />
    <spacer id="logical-length" width={20} height={10} />
  </stack>, host, { id: 'ticket43-geometry', viewport: profileInputs[0] });
  const snapshots = [root.layout()];
  for (const viewport of profileInputs.slice(1)) {
    root.resize(viewport);
    snapshots.push(root.layout());
  }
  const result = { snapshots, counts: host.counts(), offered: host.offered() };
  root.close();
  return result;
}

export function arrangementProof() {
  const scenes = [
    <row id="arrange-row" width={200} height={100} padding={10} spacing={10} justify="end" align="end">
      <spacer id="constrained-row" width={10} height={10} minWidth="25%" maxWidth="50%" minHeight="25%" maxHeight="50%" />
      <spacer id="next-row" width={20} height={10} />
    </row>,
    <column id="arrange-column" width={200} height={100} padding={10} spacing={10} justify="end" align="end">
      <spacer id="constrained-column" width={10} height={10} minWidth="25%" maxWidth="50%" minHeight="25%" maxHeight="50%" />
      <spacer id="next-column" width={20} height={10} />
    </column>,
    <stack id="arrange-stack" width={200} height={100} padding={10} anchor="bottomRight">
      <spacer id="constrained-stack" width={10} height={10} minWidth="25%" maxWidth="50%" minHeight="25%" maxHeight="50%" />
    </stack>
  ];
  const snapshots = [];
  for (const scene of scenes) {
    const host = makeHost();
    const root = UI.createRoot(() => scene, host, { id: 'ticket43-arrangement', viewport: profileInputs[1] });
    snapshots.push(root.layout());
    root.close();
  }
  return snapshots;
}

export function precedenceProof() {
  const host = makeHost();
  let renders = 0;
  const root = UI.createRoot(() => {
    renders++;
    return <stack id="precedence-root" width="fill" height="fill">
      <spacer id="base-exact-lower" width={{ base: 11, profiles: { 2: 22, 4: 44 } }} height={10} />
      <spacer id="future-fallback" width={{ profiles: { 4: 64 } }} height={10} />
      <spacer id="base-only" width={{ base: 13 }} height={10} />
      <spacer id="nearest-lower" width={{ base: 17, profiles: { 2: 23, 3: 31, 5: 53 } }} height={10} />
    </stack>;
  }, host, { id: 'ticket43-precedence', viewport: profileInputs[0] });
  const snapshots = [root.layout()];
  const before = { renders, counts: host.counts(), roots: host.roots() };
  const resizeResults = [];
  for (const viewport of profileInputs.slice(1)) {
    resizeResults.push(root.resize(viewport));
    snapshots.push(root.layout());
  }
  const result = {
    snapshots,
    resizeResults,
    before,
    after: { renders, counts: host.counts(), roots: host.roots() },
    offered: host.offered(),
    diagnostics: host.diagnostics()
  };
  root.close();
  return result;
}
