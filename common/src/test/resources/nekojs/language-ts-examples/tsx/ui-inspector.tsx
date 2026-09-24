import { UI } from 'nekojs/jsx-runtime';

// Ticket 45 fake-host fixture: renders one representative UI and exports the runtime's
// public layout snapshots for all six viewport profiles, plus the host-side facts
// (focus id, retained phase error) the Java inspector decoration needs. The Java test
// reads these guest objects through the shared InspectorSnapshots collection path.

function check(condition, message) {
  if (!condition) throw new Error('UI inspector fake-host proof failed: ' + message);
}

function makeInspectorHost() {
  const diagnostics = [];
  return {
    focusedId: null,
    isOwnerThread: () => true,
    enqueue: () => false,
    supportsPrimitive: type => UI.primitives().includes(type),
    measureText: (text, fontSize, maxWidth) => ({
      width: Math.min(maxWidth, String(text == null ? '' : text).length * Math.ceil(fontSize * 0.6)),
      height: Math.ceil(fontSize)
    }),
    layout: () => { },
    reportDiagnostic: diagnostic => diagnostics.push(diagnostic),
    begin: () => ({
      create: () => ({}),
      update: () => { },
      order: () => { },
      remove: () => { },
      commit: () => { },
      rollback: () => { }
    }),
    diagnostics: () => diagnostics.slice()
  };
}

function renderUi() {
  return <screen id="root">
    <column id="main" spacing={2} padding={4}>
      <label id="title">Panel title</label>
      <row id="actions" spacing={4}>
        <button id="act" onClick={() => { }} onBlur={() => { }}>Act</button>
        <image id="hero" resource="mymod:gui/hero" width={40} height={12} />
      </row>
      <scroll id="scroller" height={6} scrollOffset={2}>
        <label id="tall">Tall content text</label>
      </scroll>
    </column>
    <row id="wide" width={200} height={8} />
  </screen>;
}

const profileInputs = [
  { width: 100, height: 100, guiScale: 2, designWidth: 320, designHeight: 180 },
  { width: 320, height: 180, guiScale: 2, designWidth: 320, designHeight: 180 },
  { width: 480, height: 240, guiScale: 2, designWidth: 320, designHeight: 180 },
  { width: 640, height: 360, guiScale: 2, designWidth: 320, designHeight: 180 },
  { width: 854, height: 480, guiScale: 2, designWidth: 320, designHeight: 180 },
  { width: 1280, height: 720, guiScale: 2, designWidth: 320, designHeight: 180 }
];

const host = makeInspectorHost();
const root = UI.createRoot(renderUi, host, { id: 'insp-root', viewport: profileInputs[0] });
host.focusedId = 'act';
const snapshots = [root.layout()];
for (let i = 1; i < profileInputs.length; i++) {
  check(root.resize(profileInputs[i]) === true, 'resize at profile ' + (i + 1) + ' republishes the snapshot');
  snapshots.push(root.layout());
}
check(snapshots.length === 6 && snapshots.every(Boolean), 'all six profile snapshots exist');
check(snapshots.map(snapshot => snapshot.profile).join(',') === '1,2,3,4,5,6', 'six viewport profiles selected');

function findNode(nodes, id) {
  for (const node of nodes) {
    if (node.id === id) return node;
    const nested = findNode(node.children, id);
    if (nested != null) return nested;
  }
  return null;
}
const act = findNode(snapshots[5].nodes, 'act');
check(act != null && act.bindings.join(',') === 'blur,click', 'bindings summarize the bound event names');
const wide = findNode(snapshots[0].nodes, 'wide');
check(wide != null && wide.overflow.right === 100, 'fixed 200px row overflows a 100px viewport by 100');
check(snapshots[0].diagnostics.includes('wide:overflow-right'), 'overflow reaches the layout diagnostics');
const tall = findNode(snapshots[5].nodes, 'tall');
check(tall != null && tall.scrollOffset == null && findNode(snapshots[5].nodes, 'scroller').style.scrollOffset === 2,
  'scroll offset stays a resolved style fact on the scroller');

let layoutError = null;
try {
  UI.createRoot(() => <row minWidth={10} maxWidth={1} />, host, { id: 'insp-error-root', viewport: { width: 320, height: 180 } });
} catch (error) {
  layoutError = error;
}
check(layoutError != null, 'a broken layout fails the initial reconcile');
const reported = host.diagnostics()[host.diagnostics().length - 1];
check(reported != null && reported.phase === 'layout' && reported.rootId === 'insp-error-root',
  'the layout failure is reported with its phase and root');

export const inspectorProof = Object.freeze({
  source: 'fake-host',
  rootId: 'insp-root',
  focusedId: host.focusedId,
  snapshots: Object.freeze(snapshots.map(snapshot => snapshot)),
  error: Object.freeze({
    phase: reported.phase,
    rootId: reported.rootId,
    message: String(reported.error && reported.error.message ? reported.error.message : reported.error)
  }),
  passed: true
});
