import { UI } from 'nekojs/jsx-runtime';

function check(condition, message) {
  if (!condition) throw new Error('UI fake-host proof failed: ' + message);
}

function makeHost() {
  let nodes = new Map();
  let roots = [];
  let nextId = 1;
  let owner = true;
  let acceptingQueue = true;
  let queue = [];
  const diagnostics = [];
  let layoutSnapshot = null;
  const counts = { commits: 0, rollbacks: 0, layouts: 0 };
  const host = {
    failLayout: false,
    failUpdate: false,
    failCommit: false,
    onCommit: null,
    onUpdate: null,
    onLayout: null,
    unsupportedPrimitive: null,
    isOwnerThread: () => owner,
    enqueue: action => {
      if (!acceptingQueue) return false;
      queue.push(action);
      return true;
    },
    supportsPrimitive: type => type !== host.unsupportedPrimitive && UI.primitives().includes(type),
    measureText: (text, fontSize, maxWidth) => ({
      width: Math.min(maxWidth, String(text == null ? '' : text).length * Math.ceil(fontSize * 0.6)),
      height: Math.ceil(fontSize)
    }),
    layout: (tree, viewport, snapshot) => {
      counts.layouts++;
      if (host.failLayout) throw new Error('fake layout failure');
      check(Object.isFrozen(tree), 'layout receives an immutable candidate');
      if (snapshot != null) layoutSnapshot = snapshot;
      if (host.onLayout != null) host.onLayout();
    },
    reportDiagnostic: diagnostic => diagnostics.push(diagnostic),
    begin: () => {
      const staged = new Map(Array.from(nodes, ([id, node]) => [id, {
        id: node.id,
        type: node.type,
        key: node.key,
        props: { ...node.props },
        children: node.children.slice()
      }]));
      let stagedRoots = roots.slice();
      return {
        create: (type, key, props) => {
          const id = nextId++;
          staged.set(id, { id, type, key, props: { ...props }, children: [] });
          return id;
        },
        update: (id, type, key, props) => {
          if (host.failUpdate) throw new Error('fake update failure');
          const node = staged.get(id);
          check(node != null, 'updates target staged host nodes');
          node.type = type;
          node.key = key;
          node.props = { ...props };
          if (host.onUpdate != null) host.onUpdate();
        },
        order: (parent, children) => {
          if (parent == null) stagedRoots = children.slice();
          else staged.get(parent).children = children.slice();
        },
        remove: id => staged.delete(id),
        commit: nextRoots => {
          if (host.failCommit) throw new Error('fake commit failure');
          check(stagedRoots.join(',') === nextRoots.join(','), 'root order is part of the transaction');
          nodes = staged;
          roots = stagedRoots.slice();
          counts.commits++;
          if (host.onCommit != null) host.onCommit();
        },
        rollback: () => { counts.rollbacks++; }
      };
    },
    snapshot: () => roots.map(id => snapshotNode(id)),
    layoutSnapshot: () => layoutSnapshot,
    findById: id => findNode(roots, id),
    lastPhase: () => diagnostics.length === 0 ? null : diagnostics[diagnostics.length - 1].phase,
    diagnosticCount: () => diagnostics.length,
    counts: () => ({ ...counts }),
    setOwner: value => { owner = value; },
    rejectQueue: value => { acceptingQueue = !value; },
    flush: () => {
      owner = true;
      const pending = queue;
      queue = [];
      for (const action of pending) action();
    }
  };
  function snapshotNode(id) {
    const node = nodes.get(id);
    return { identity: node.id, type: node.type, key: node.key, props: node.props, children: node.children.map(snapshotNode) };
  }
  function findNode(ids, wanted) {
    for (const handle of ids) {
      const node = nodes.get(handle);
      if (node.props.id === wanted) return node.id;
      const nested = findNode(node.children, wanted);
      if (nested != null) return nested;
    }
    return null;
  }
  return host;
}

const mapHost = makeHost();
mapHost.viewport = () => new Map([['width', 320], ['height', 180]]);
const mapRoot = UI.createRoot(() => UI.element('screen', { id: 'map-root' }), mapHost, { id: 'map-root' });
check(mapHost.layoutSnapshot().profile === 2, 'host Map viewport reaches initial layout');
check(mapRoot.resize(new Map([['width', 1280], ['height', 720]])) === true
  && mapHost.layoutSnapshot().profile === 6, 'host Map viewport reaches resize');
mapRoot.close();

const loweredSingle = <screen id="lowered" />;
const loweredMany = <column><label>one</label><label>two</label></column>;
const spreadProps = { id: 'spread' };
const loweredSpread = <label {...spreadProps} key="spread-key" />;
const loweredFragment = <><label>fragment</label></>;
check(Object.isFrozen(loweredSingle) && loweredSingle.type === 'screen', 'automatic JSX emits immutable VNodes');
check(loweredMany.children.length === 2, 'jsxs retains its children');
check(loweredSpread.props.id === 'spread' && loweredSpread.key === 'spread-key', 'spread props and explicit keys survive lowering');
check(loweredFragment.type === UI.fragment, 'Fragment uses the runtime identity');

const inputProps = { id: 'snapshot', data: { value: 1 }, children: 'frozen' };
const snapshotVNode = UI.element('label', inputProps, 'snapshot-key');
inputProps.data.value = 2;
check(Object.isFrozen(snapshotVNode.props) && snapshotVNode.props.data.value === 1, 'VNode props are immutable snapshots');
check(snapshotVNode.key === 'snapshot-key', 'automatic runtime preserves keys');
check(UI.primitives().slice().sort().join(',') === 'button,column,dialog,image,input,item,label,panel,row,screen,scroll,slider,spacer,stack,window,workspace', 'primitive registry is complete');
const profileWidths = [100, 320, 480, 640, 854, 1280];
const profileHeights = [100, 180, 240, 360, 480, 720];
const selectedProfiles = profileWidths.map((width, index) => UI.profileFor({ width, height: profileHeights[index] }));
check(selectedProfiles.join(',') === '1,2,3,4,5,6', 'logical viewport boundaries select all six profiles');
check(UI.profileFor({ width: 1280, height: 720, guiScale: 1 }) === 6, 'GUI scale does not replace logical viewport selection');
check(UI.profileFor({ width: 1280, height: 720, capabilities: { maxProfile: 3 } }) === 3, 'capability maxProfile caps selection');
check(UI.resolveViewport({ width: 1280, height: 720, designWidth: 640, designHeight: 360 }).designScale === 2, 'design coordinates expose a controlled scale');
let invalidViewport = false;
try { UI.profileFor({ width: 0, height: 180 }); } catch (_) { invalidViewport = true; }
check(invalidViewport, 'zero viewport is rejected');

const host = makeHost();
const mode = UI.createSignal('good');
const useAlternate = UI.createSignal(false);
const primary = UI.createSignal('primary');
const alternate = UI.createSignal('alternate');
const updateProbe = UI.createSignal(0);
const store = UI.createStore({ items: ['a', 'b'] });
let renders = 0;
let handlerVersion = 1;
const eventCalls = [];
const badEventCalls = [];

function Item(props) {
  return UI.element('button', {
    id: 'item-' + props.item,
    children: props.item,
    onClick: () => eventCalls.push(props.item)
  }, props.item);
}
function Broken() { throw new Error('component failure'); }
function render() {
  renders++;
  const currentMode = mode.get();
  if (currentMode === 'render-error') throw new Error('render failure');
  if (currentMode === 'component-error') return UI.element('screen', { children: UI.element(Broken, {}) });
  if (currentMode === 'layout-error') {
    return UI.element('screen', { children: UI.element('row', { minWidth: 10, maxWidth: 1 }) });
  }
  if (currentMode === 'unknown-primitive') return UI.element('screen', { children: UI.element('div', {}) });
  if (currentMode === 'invalid-prop') return UI.element('screen', { children: UI.element('label', { unsupported: 'no' }) });
  if (currentMode === 'invalid-profile') return UI.element('screen', { children: UI.element('label', { width: { base: 10, profiles: { 7: 20 } } }) });
  if (currentMode === 'responsive-minmax') return UI.element('screen', { children: UI.element('panel', { width: '50%', minWidth: { base: 20, profiles: { 6: 200 } }, maxWidth: { base: 100, profiles: { 6: 100 } } }) });
  if (currentMode === 'duplicate-key') {
    return UI.element('screen', { children: UI.element('row', { children: [Item({ item: 'x' }), Item({ item: 'x' })] }) });
  }
  if (currentMode === 'fragment-duplicate-key') {
    const keyed = () => UI.element('button', { children: 'same' }, 'same');
    return UI.element('screen', { children: UI.element('row', { children: [
      UI.element(UI.fragment, { children: keyed() }),
      UI.element(UI.fragment, { children: keyed() })
    ] }) });
  }
  const visibleValue = useAlternate.get() ? alternate.get() : primary.get();
  updateProbe.get();
  const currentHandler = handlerVersion;
  const items = store.get('items');
  const children = [
    UI.element('row', { id: 'items', gap: { base: 4, profiles: { 1: 2 } }, direction: { base: 'row', profiles: { 1: 'column' } }, children: items.map(item => UI.element(Item, { item }, item)) }),
    UI.element('panel', { id: 'probe', width: { base: '50%', profiles: { 6: '25%' } }, height: { base: 60, profiles: { 6: 80 } }, minWidth: { base: 80, profiles: { 6: 120 } }, maxWidth: { base: 90, profiles: { 6: 400 } }, padding: { base: 4, profiles: { 6: 8 } }, children: UI.element('label', { id: 'probe-text', width: '120%' }, 'probe') }),
    UI.element('stack', { id: 'stack-probe', width: 100, height: 50, children: UI.element('spacer', { id: 'anchor-probe', width: 20, height: 10, anchor: 'bottomRight' }) }),
    UI.element('scroll', { id: 'scroll-probe', width: 100, height: 30, scrollOffset: { base: 0, profiles: { 6: 12 } }, children: UI.element('spacer', { id: 'scroll-child', width: 100, height: 40 }) }),
    UI.element('label', { id: 'hidden-probe', visible: { base: true, profiles: { 6: false } }, children: 'hidden' }),
    UI.element('label', { id: 'value', fontSize: { base: 9, profiles: { 6: 12 } }, children: String(visibleValue) }),
    UI.element('label', { id: 'conditional', children: currentMode === 'good' ? 'ready' : currentMode }),
    UI.element('button', { id: 'replace-event', children: 'Replace', onClick: () => eventCalls.push(currentHandler) }),
    UI.element('button', { id: 'bad-event', children: 'Bad', onClick: () => { badEventCalls.push('called'); throw new Error('event failure'); } }),
    UI.element('button', { id: 'good-event', children: 'Good', onClick: event => eventCalls.push(event.target) }),
    UI.element('panel', { id: 'visual-panel', opacity: 0.5, children: UI.element('label', { id: 'truncate-label', width: 40, truncate: true, children: 'truncation entry' }) }),
    UI.element('image', { id: 'visual-image', resource: 'mymod:gui/hero', fit: 'contain', opacity: 0.25, icon: 'mymod:gui/icon', crop: { x: 1, y: 2, width: 8, height: 9 } })
  ];
  return UI.element('screen', { id: 'screen', title: 'UI proof', pausesGame: false, children });
}

const root = UI.createRoot(render, host, { id: 'ticket40-root' });
check(root.id === 'ticket40-root' && host.snapshot()[0].type === 'screen', 'initial tree commits through the public host contract');
const keyedA = host.findById('item-a');
const keyedB = host.findById('item-b');
const initialProbe = host.layoutSnapshot().nodes[0].children.find(node => node.id === 'probe');
check(initialProbe != null && initialProbe.rect.width === 90, 'base profile applies percentage and max width');
const initialLayouts = host.counts().layouts;
const initialRenders = renders;
const initialCommits = host.counts().commits;
check(root.resize({ width: 480, height: 240 }) === true, 'resize recomputes layout');
check(host.layoutSnapshot().profile === 3, 'resize selects profile three');
check(renders === initialRenders && host.counts().commits === initialCommits, 'resize does not render or rebuild host nodes');
check(host.counts().layouts === initialLayouts + 1, 'resize performs one layout pass');
check(root.resize({ width: 1280, height: 720 }) === true && host.layoutSnapshot().profile === 6, 'resize reaches profile six');
const probe = host.layoutSnapshot().nodes[0].children.find(node => node.id === 'probe');
check(probe != null && probe.rect.width === 320, 'profile layout applies percentage and max width');
check(probe.children[0].overflow.right > 0, 'layout output exposes clipping overflow');
const stackProbe = host.layoutSnapshot().nodes[0].children.find(node => node.id === 'stack-probe');
check(stackProbe.children[0].rect.x > stackProbe.rect.x && stackProbe.children[0].rect.y > stackProbe.rect.y, 'stack anchor arranges the child');
const scrollProbe = host.layoutSnapshot().nodes[0].children.find(node => node.id === 'scroll-probe');
check(scrollProbe.style.scrollOffset === 12 && scrollProbe.children[0].overflow.top > 0, 'scroll profile override clips offset content');
const hiddenProbe = host.layoutSnapshot().nodes[0].children.find(node => node.id === 'hidden-probe');
check(hiddenProbe.visible === false && hiddenProbe.rect.width === 0, 'profile visibility hides a node');
const valueProbe = host.layoutSnapshot().nodes[0].children.find(node => node.id === 'value');
check(valueProbe.style.fontSize === 12, 'profile text size override is observable');
function nodeById(id) {
  const visit = node => {
    if (node.props.id === id) return node;
    for (const child of node.children) {
      const found = visit(child);
      if (found) return found;
    }
    return null;
  };
  for (const node of host.snapshot()) {
    const found = visit(node);
    if (found) return found;
  }
  return null;
}
const visualImage = nodeById('visual-image');
check(visualImage != null && visualImage.props.resource === 'mymod:gui/hero' && visualImage.props.fit === 'contain'
  && visualImage.props.opacity === 0.25 && visualImage.props.icon === 'mymod:gui/icon'
  && visualImage.props.crop.width === 8 && visualImage.props.crop.height === 9, 'image visual props cross the script whitelist to host nodes');
const visualPanel = nodeById('visual-panel');
check(visualPanel != null && visualPanel.props.opacity === 0.5, 'panel opacity crosses the script whitelist');
const truncateLabel = nodeById('truncate-label');
check(truncateLabel != null && truncateLabel.props.truncate === true && truncateLabel.props.text === 'truncation entry',
  'label truncate crosses the script whitelist');

store.set('items', ['b', 'a', 'c']);
check(host.findById('item-a') === keyedA && host.findById('item-b') === keyedB, 'keyed reorder retains host identity');
check(host.snapshot()[0].children[0].children.map(node => node.key).join(',') === 'b,a,c', 'children reorder in the committed tree');

const beforeSwitch = renders;
const layoutsBeforeSwitch = host.counts().layouts;
useAlternate.set(true);
const afterSwitch = renders;
check(host.counts().layouts === layoutsBeforeSwitch + 1, 'signal updates invalidate and rerun layout');
check(afterSwitch === beforeSwitch + 1, 'signal writes reconcile an affected root');
primary.set('stale');
check(renders === afterSwitch, 'dependencies rebuild after a successful render');
alternate.set('fresh');
check(renders === afterSwitch + 1, 'the replacement dependency invalidates the root');
const beforeBatch = renders;
UI.batch(() => { alternate.set('batch-one'); alternate.set('batch-two'); });
check(renders === beforeBatch + 1, 'batch coalesces invalidation');
check(store.snapshot().items.join(',') === 'b,a,c', 'store reads and writes are observable');

const beforeQueue = renders;
host.setOwner(false);
check(alternate.set('queued') === 'queued', 'off-thread writes report queueing');
check(alternate.get() === 'batch-two' && renders === beforeQueue, 'queued writes do not mutate early');
host.flush();
check(alternate.get() === 'queued' && renders === beforeQueue + 1, 'queued writes run on the owner thread');
host.setOwner(false);
let updateCalls = 0;
check(updateProbe.update(value => { updateCalls++; return value + 1; }) === 'queued', 'off-thread updater reports queueing');
check(updateProbe.update(value => { updateCalls++; return value + 1; }) === 'queued', 'a second updater also queues');
check(updateCalls === 0 && updateProbe.get() === 0, 'off-thread updaters do not run or mutate early');
host.flush();
check(updateCalls === 2 && updateProbe.get() === 2, 'queued updaters run twice with the latest owner value');
host.setOwner(false);
host.rejectQueue(true);
let rejected = false;
try { alternate.set('rejected'); } catch (_) { rejected = true; }
let rejectedUpdateCalls = 0;
let rejectedUpdate = false;
try { updateProbe.update(value => { rejectedUpdateCalls++; return value + 1; }); } catch (_) { rejectedUpdate = true; }
check(rejected && alternate.get() === 'queued', 'rejected off-thread writes leave state unchanged');
check(rejectedUpdate && rejectedUpdateCalls === 0 && updateProbe.get() === 2, 'rejected updater never runs');
host.setOwner(true);
host.rejectQueue(false);

const stableTree = () => JSON.stringify(host.snapshot());
const beforeInvalid = stableTree();
for (const [value, phase] of [
  ['render-error', 'render'], ['component-error', 'component'], ['layout-error', 'layout'],
  ['unknown-primitive', 'layout'], ['invalid-prop', 'layout'], ['invalid-profile', 'layout'],
  ['responsive-minmax', 'layout'], ['duplicate-key', 'layout'], ['fragment-duplicate-key', 'layout']
]) {
  mode.set(value);
  check(host.lastPhase() === phase && stableTree() === beforeInvalid, phase + ' failure keeps the active tree');
}
mode.set('good');
host.unsupportedPrimitive = 'label';
const beforeCapabilityFailure = stableTree();
root.refresh();
check(host.lastPhase() === 'host-update' && stableTree() === beforeCapabilityFailure, 'unsupported host capability rejects the candidate');
host.unsupportedPrimitive = null;
root.refresh();

const eventErrors = host.diagnosticCount();
check(root.dispatch('bad-event', 'click', { x: 2, y: 3 }) === false, 'event errors are isolated');
check(badEventCalls.length === 1 && host.lastPhase() === 'event', 'event failure has an event diagnostic');
check(root.dispatch('good-event', 'click', {}) === true && eventCalls.includes('good-event'), 'other controls still dispatch');
check(host.diagnosticCount() === eventErrors + 1, 'event failure reports once');
root.dispatch('replace-event', 'click', {});
handlerVersion = 2;
root.refresh();
root.dispatch('replace-event', 'click', {});
check(eventCalls.slice(-2).join(',') === '1,2', 'new event closures replace old handlers');

const beforeHostFailure = stableTree();
const beforeRollback = host.counts().rollbacks;
host.failUpdate = true;
alternate.set('host-failure');
check(host.lastPhase() === 'host-update' && stableTree() === beforeHostFailure, 'failed host update retains last valid tree');
check(host.counts().rollbacks === beforeRollback + 1, 'failed candidate transaction rolls back');
host.failUpdate = false;
root.refresh();

const beforeCommitFailure = stableTree();
host.failCommit = true;
alternate.set('commit-failure');
check(host.lastPhase() === 'host-update' && stableTree() === beforeCommitFailure, 'failed commit does not publish candidate state');
host.failCommit = false;
root.refresh();

const commitsBeforeClose = host.counts().commits;
host.failCommit = true;
check(root.close() === false && !root.isDisposed() && host.snapshot().length > 0, 'failed close retains active host tree for retry');
host.failCommit = false;
check(root.close() === true && root.close() === false, 'close is idempotent');
check(root.isDisposed() && host.snapshot().length === 0, 'close releases host nodes');
check(host.counts().commits === commitsBeforeClose + 1, 'second close performs no host work');
const rendersAfterClose = renders;
alternate.set('after-close');
check(renders === rendersAfterClose && root.dispatch('good-event', 'click', {}) === false, 'disposed roots detach');
let disposedRejected = false;
try { root.refresh(); } catch (_) { disposedRejected = true; }
check(disposedRejected, 'disposed handles reject refresh');

const componentHost = makeHost();
const componentOrder = UI.createSignal(['left', 'right']);
function ScopedCell(props) {
  return UI.element('input', { id: 'scoped-' + props.name, value: props.name }, 'inner');
}
const componentRoot = UI.createRoot(() => UI.element('row', {
  children: componentOrder.get().map(name => UI.element(ScopedCell, { name }, name))
}), componentHost);
const leftHandle = componentHost.findById('scoped-left');
const rightHandle = componentHost.findById('scoped-right');
check(leftHandle !== rightHandle, 'component-local keys create distinct host handles');
componentOrder.set(['right', 'left']);
check(componentHost.findById('scoped-left') === leftHandle
  && componentHost.findById('scoped-right') === rightHandle,
  'keyed function component reorder preserves each local host identity');
check(componentHost.snapshot()[0].children.map(node => node.identity).join(',') === [rightHandle, leftHandle].join(','),
  'component-local keys never reuse one handle for two siblings');
check(componentHost.snapshot()[0].children.every(node => node.key === 'inner'),
  'component identity does not rewrite public host keys');
componentRoot.close();

const multiHost = makeHost();
const otherRootHost = makeHost();
const multiOrder = UI.createSignal(['left', 'right']);
function MultiCell(props) {
  return ['x', 'y'].map(local => UI.element('label', {
    id: props.name + '-' + local, text: props.name + local
  }, local));
}
const multiRoot = UI.createRoot(() => multiOrder.get().map(name => UI.element(MultiCell, { name }, name)), multiHost);
const otherRoot = UI.createRoot(() => ['left', 'right'].map(name => UI.element(MultiCell, { name }, name)), otherRootHost);
const multiHandles = multiHost.snapshot().map(node => node.identity);
const otherRootBefore = JSON.stringify(otherRootHost.snapshot());
const otherRootCommits = otherRootHost.counts().commits;
multiOrder.set(['right', 'left']);
check(multiHost.snapshot().map(node => node.identity).join(',') ===
  [multiHandles[2], multiHandles[3], multiHandles[0], multiHandles[1]].join(','),
  'keyed multi-root components retain every local host identity while reordering');
check(multiHost.snapshot().map(node => node.key).join(',') === 'x,y,x,y',
  'multi-root local public keys remain unchanged');
check(JSON.stringify(otherRootHost.snapshot()) === otherRootBefore && otherRootHost.counts().commits === otherRootCommits,
  'component identity and signal reconciliation remain isolated per root');
multiRoot.close();
otherRoot.close();

const remountHost = makeHost();
let componentVersion = false;
let componentOuterKey = 'same';
function FirstVersion() { return UI.element('label', { id: 'version', text: 'first' }, 'local'); }
function SecondVersion() { return UI.element('label', { id: 'version', text: 'second' }, 'local'); }
const remountRoot = UI.createRoot(() => UI.element(componentVersion ? SecondVersion : FirstVersion, {}, componentOuterKey), remountHost);
const firstVersionHandle = remountHost.findById('version');
componentVersion = true;
check(remountRoot.refresh() === true && remountHost.findById('version') !== firstVersionHandle,
  'changing function component type remounts even when outer and local public keys match');
const secondVersionHandle = remountHost.findById('version');
componentOuterKey = 'replacement';
check(remountRoot.refresh() === true && remountHost.findById('version') !== secondVersionHandle,
  'changing a component outer key remounts its local output');
remountRoot.close();

const positionalHost = makeHost();
let positionalOrder = ['left', 'right'];
let duplicateComponents = false;
const positionalRoot = UI.createRoot(() => positionalOrder.map(name =>
  UI.element(ScopedCell, { name }, duplicateComponents ? 'duplicate' : undefined)), positionalHost);
const positionalHandles = positionalHost.snapshot().map(node => node.identity).join(',');
positionalOrder = ['right', 'left'];
check(positionalRoot.refresh() === true
  && positionalHost.snapshot().map(node => node.identity).join(',') === positionalHandles
  && positionalHost.snapshot().map(node => node.props.value).join(',') === 'right,left',
  'unkeyed component reorder preserves positional host identities');
const beforeDuplicateComponents = JSON.stringify(positionalHost.snapshot());
const commitsBeforeDuplicateComponents = positionalHost.counts().commits;
duplicateComponents = true;
check(positionalRoot.refresh() === false && positionalHost.counts().commits === commitsBeforeDuplicateComponents
  && JSON.stringify(positionalHost.snapshot()) === beforeDuplicateComponents,
  'duplicate sibling component keys reject atomically instead of aliasing host handles');
positionalRoot.close();

const reentryHost = makeHost();
const reentryValue = UI.createSignal(0);
const reentryRoot = UI.createRoot(() => {
  const value = reentryValue.get();
  if (value === 1) reentryValue.set(2);
  return UI.element('label', { id: 'reentry-value', text: String(value) });
}, reentryHost);
reentryValue.set(1);
check(reentryValue.get() === 2 && reentryHost.snapshot()[0].props.text === '2',
  'render-phase signal writes cannot let stale outer output overwrite the latest value');
reentryRoot.close();

const forcedHost = makeHost();
const forcedValue = UI.createSignal(0);
let forcedRenders = 0;
let forcedEvents = 0;
const forcedRoot = UI.createRoot(() => {
  forcedRenders++;
  return UI.element('button', { id: 'forced-event', text: String(forcedValue.get()),
    onClick: () => forcedEvents++ });
}, forcedHost);
forcedHost.failCommit = true;
check(forcedRoot.close() === false && !forcedRoot.isDisposed(),
  'ordinary failed close stays retryable before generation teardown');
forcedRoot.forceClose();
check(forcedRoot.isDisposed() && forcedRoot.layout() === null,
  'forced close irreversibly detaches even when host cleanup fails');
const forcedRendersAfterClose = forcedRenders;
forcedValue.set(1);
check(forcedRenders === forcedRendersAfterClose
  && forcedRoot.dispatch('forced-event', 'click', {}) === false && forcedEvents === 0,
  'forced close releases signal subscriptions and event callbacks');
const forcedCommitsAfterClose = forcedHost.counts().commits;
forcedRoot.forceClose();
check(forcedHost.counts().commits === forcedCommitsAfterClose,
  'repeated forced close performs no host work');

const busyHost = makeHost();
let busyRoot = null;
let checkBusy = false;
let checkedComponent = false;
function BusyChild() {
  if (checkBusy) {
    check(busyRoot.isUpdating(), 'updating guard covers nested component expansion');
    checkedComponent = true;
  }
  return UI.element('label', { text: 'busy child' });
}
busyRoot = UI.createRoot(() => {
  if (checkBusy) {
    check(busyRoot.isUpdating(), 'updating guard covers render callback');
    check(busyRoot.close() === false && !busyRoot.isDisposed(),
      'ordinary close during reconciliation rejects without host overlap and remains retryable');
  }
  return UI.element(BusyChild, {});
}, busyHost);
check(!busyRoot.isUpdating(), 'updating guard is false while idle');
busyHost.onCommit = () => check(busyRoot.isUpdating(), 'updating guard covers host commit callback');
checkBusy = true;
check(busyRoot.refresh() === true && checkedComponent && !busyRoot.isUpdating(),
  'guard remains active for the whole refresh then resets');
busyHost.onCommit = null;
busyRoot.close();
check(!busyRoot.isUpdating(), 'disposed roots are not updating');

const busyResizeHost = makeHost();
const busyResizeRoot = UI.createRoot(() => UI.element('label', { text: 'resize boundary' }), busyResizeHost);
const beforeBusyResize = busyResizeRoot.layout();
let busyResizeResult = null;
busyResizeHost.onCommit = () => {
  busyResizeHost.onCommit = null;
  busyResizeResult = busyResizeRoot.resize({ width: 640, height: 360 });
};
check(busyResizeRoot.refresh() === true && busyResizeResult === false
  && busyResizeRoot.layout().viewport.width === beforeBusyResize.viewport.width,
  'resize during reconciliation rejects without overlapping host layout or mutating the published viewport');
check(busyResizeRoot.resize({ width: 640, height: 360 }) === true
  && busyResizeRoot.layout().viewport.width === 640,
  'rejected busy resize remains retryable after reconciliation');
busyResizeRoot.close();

const resizeDisposeHost = makeHost();
const resizeDisposeRoot = UI.createRoot(() => UI.element('label', { text: 'resize disposal' }), resizeDisposeHost);
let resizeWasUpdating = false;
resizeDisposeHost.onLayout = () => {
  resizeDisposeHost.onLayout = null;
  resizeWasUpdating = resizeDisposeRoot.isUpdating();
  resizeDisposeRoot.forceClose();
};
check(resizeDisposeRoot.resize({ width: 640, height: 360 }) === false && resizeWasUpdating
  && resizeDisposeRoot.isDisposed() && resizeDisposeRoot.layout() === null
  && resizeDisposeHost.snapshot().length === 0,
  'resize layout is guarded and forced disposal cannot resurrect its layout snapshot');

const resizeReentryHost = makeHost();
const resizeReentryValue = UI.createSignal(0);
const resizeReentryRoot = UI.createRoot(() => UI.element('label', { text: String(resizeReentryValue.get()) }), resizeReentryHost);
const commitsBeforeResizeReentry = resizeReentryHost.counts().commits;
let resizeReentryBusy = false;
let resizeRefreshResult = null;
let commitsInsideResize = -1;
resizeReentryHost.onLayout = () => {
  resizeReentryHost.onLayout = null;
  resizeReentryBusy = resizeReentryRoot.isUpdating();
  resizeReentryValue.set(1);
  resizeRefreshResult = resizeReentryRoot.refresh();
  commitsInsideResize = resizeReentryHost.counts().commits;
};
check(resizeReentryRoot.resize({ width: 640, height: 360 }) === true && resizeReentryBusy
  && resizeRefreshResult === 'queued' && commitsInsideResize === commitsBeforeResizeReentry
  && resizeReentryHost.counts().commits === commitsBeforeResizeReentry + 1
  && resizeReentryHost.snapshot()[0].props.text === '1' && resizeReentryRoot.layout().viewport.width === 640,
  'refresh and signal writes from resize layout serialize after layout without nested host publication');
resizeReentryRoot.close();

const renderCloseHost = makeHost();
const renderCloseValue = UI.createSignal(0);
let renderCloseRoot = null;
let renderCloseRenders = 0;
let renderCloseChildRenders = 0;
function RenderCloseChild() {
  renderCloseChildRenders++;
  return UI.element('label', { text: 'disposed child' });
}
renderCloseRoot = UI.createRoot(() => {
  renderCloseRenders++;
  const value = renderCloseValue.get();
  if (value === 1) {
    renderCloseRoot.forceClose();
    return UI.element(RenderCloseChild, {});
  }
  return UI.element('label', { text: String(value) });
}, renderCloseHost);
renderCloseValue.set(1);
check(renderCloseChildRenders === 0, 'forced close during render stops returned child component expansion');
check(renderCloseRoot.isDisposed() && renderCloseRoot.layout() === null
  && renderCloseHost.snapshot().length === 0,
  'forced close during render cannot publish or resurrect a disposed candidate');
const rendersAfterRenderClose = renderCloseRenders;
renderCloseValue.set(2);
check(renderCloseRenders === rendersAfterRenderClose,
  'render-phase forced close cannot reattach observers');

const componentCloseHost = makeHost();
const componentCloseValue = UI.createSignal(0);
let componentCloseRoot = null;
let disposedLateCalls = 0;
let disposedSiblingCalls = 0;
function DisposedLateChild() {
  disposedLateCalls++;
  return UI.element('label', { text: 'late disposed child' });
}
function DisposingChild() {
  if (componentCloseValue.get() === 1) {
    componentCloseRoot.forceClose();
    return UI.element(DisposedLateChild, {});
  }
  return UI.element('label', { text: 'active child' });
}
function AfterDisposingSibling() {
  disposedSiblingCalls++;
  return UI.element('label', { text: 'later sibling' });
}
componentCloseRoot = UI.createRoot(() => UI.element('column', { children: [
  UI.element(DisposingChild, {}), UI.element(AfterDisposingSibling, {})
] }), componentCloseHost);
const siblingCallsBeforeDispose = disposedSiblingCalls;
const layoutsBeforeComponentDispose = componentCloseHost.counts().layouts;
componentCloseValue.set(1);
check(disposedLateCalls === 0 && disposedSiblingCalls === siblingCallsBeforeDispose,
  'forced close inside a component stops returned child and later sibling guest expansion');
check(componentCloseRoot.isDisposed() && componentCloseRoot.layout() === null
  && componentCloseHost.snapshot().length === 0
  && componentCloseHost.counts().layouts === layoutsBeforeComponentDispose,
  'component-phase disposal skips candidate layout and clears the previously committed host tree');

const commitCloseHost = makeHost();
const commitCloseValue = UI.createSignal(0);
let commitCloseRenders = 0;
const commitCloseRoot = UI.createRoot(() => {
  commitCloseRenders++;
  return UI.element('label', { text: String(commitCloseValue.get()) });
}, commitCloseHost);
commitCloseHost.onCommit = () => {
  commitCloseHost.onCommit = null;
  commitCloseRoot.forceClose();
};
commitCloseValue.set(1);
check(commitCloseRoot.isDisposed() && commitCloseRoot.layout() === null
  && commitCloseHost.snapshot().length === 0,
  'forced close during commit clears the just-published candidate without resurrection');
const rendersAfterCommitClose = commitCloseRenders;
commitCloseValue.set(2);
check(commitCloseRenders === rendersAfterCommitClose,
  'commit-phase forced close cannot reattach observers');

const initialWriteHost = makeHost();
const initialWriteValue = UI.createSignal(0);
const initialWriteRoot = UI.createRoot(() => {
  const value = initialWriteValue.get();
  if (value === 0) initialWriteValue.set(1);
  return UI.element('label', { text: String(value) });
}, initialWriteHost);
check(initialWriteHost.counts().commits === 1 && initialWriteHost.snapshot()[0].props.text === '1',
  'initial render signal writes stabilize before the first host commit');
initialWriteRoot.close();

const sharedValue = UI.createSignal(0);
const sharedHostA = makeHost();
const sharedHostB = makeHost();
const sharedRootA = UI.createRoot(() => UI.element('label', { text: String(sharedValue.get()) }), sharedHostA);
const sharedRootB = UI.createRoot(() => {
  const value = sharedValue.get();
  if (value === 1) sharedValue.set(2);
  return UI.element('label', { text: String(value) });
}, sharedHostB);
sharedValue.set(1);
check(sharedHostA.snapshot()[0].props.text === '2' && sharedHostB.snapshot()[0].props.text === '2',
  'cross-root nested updates settle every observing root without stale output');
sharedRootA.close();
sharedRootB.close();

const boundedHost = makeHost();
const boundedValue = UI.createSignal(0);
let keepWriting = false;
let boundedRenders = 0;
const boundedRoot = UI.createRoot(() => {
  boundedRenders++;
  const value = boundedValue.get();
  if (keepWriting) boundedValue.set(value + 1);
  return UI.element('label', { text: String(value) });
}, boundedHost);
const beforeBound = boundedHost.counts();
const rendersBeforeBound = boundedRenders;
keepWriting = true;
check(boundedRoot.refresh() === false && boundedRenders - rendersBeforeBound === 32
  && boundedHost.counts().commits === beforeBound.commits && boundedHost.snapshot()[0].props.text === '0'
  && boundedHost.lastPhase() === 'render' && boundedHost.diagnosticCount() === 1,
  'non-stabilizing updates stop after 32 passes, return false, and preserve the last committed tree');
keepWriting = false;
check(boundedRoot.refresh() === true && boundedHost.snapshot()[0].props.text === '32',
  'a bounded-loop failure remains recoverable');
boundedRoot.close();

const stagingHost = makeHost();
const stagingValue = UI.createSignal(0);
const stagingRoot = UI.createRoot(() => UI.element('label', { text: String(stagingValue.get()) }), stagingHost);
const beforeStaging = stagingHost.counts();
stagingHost.onUpdate = () => {
  stagingHost.onUpdate = null;
  stagingValue.set(2);
};
stagingValue.set(1);
check(stagingHost.snapshot()[0].props.text === '2'
  && stagingHost.counts().commits === beforeStaging.commits + 1
  && stagingHost.counts().rollbacks === beforeStaging.rollbacks + 1,
  'precommit reentry rolls back the stale staged candidate and commits only its stable replacement');
stagingRoot.close();

const followupHost = makeHost();
let failFollowup = false;
const followupRoot = UI.createRoot(() => {
  if (failFollowup) throw new Error('expected follow-up render failure');
  return UI.element('label', { text: 'committed' });
}, followupHost);
const beforeFollowup = followupHost.counts().commits;
followupHost.onCommit = () => {
  followupHost.onCommit = null;
  failFollowup = true;
  check(followupRoot.refresh() === 'queued', 'commit callback refresh is serialized');
};
check(followupRoot.refresh() === true && followupHost.counts().commits === beforeFollowup + 1
  && followupHost.diagnosticCount() === 1 && followupHost.snapshot()[0].props.text === 'committed',
  'a failed follow-up cannot turn an already committed refresh into false');
followupRoot.close();

const recursiveCloseHost = makeHost();
const recursiveCloseRoot = UI.createRoot(() => UI.element('label', { text: 'close' }), recursiveCloseHost);
let clearCallbacks = 0;
recursiveCloseHost.onCommit = () => {
  clearCallbacks++;
  if (clearCallbacks === 1) {
    check(recursiveCloseRoot.isUpdating(), 'updating guard covers active host cleanup');
    recursiveCloseRoot.forceClose();
  }
};
check(recursiveCloseRoot.close() === true && clearCallbacks === 1
  && recursiveCloseRoot.isDisposed() && recursiveCloseHost.snapshot().length === 0,
  'forced close from an ordinary clear callback never starts a nested clear transaction');

const clearingSignalHost = makeHost();
const clearingSignal = UI.createSignal(0);
let clearingSignalRenders = 0;
const clearingSignalRoot = UI.createRoot(() => {
  clearingSignalRenders++;
  return UI.element('label', { text: String(clearingSignal.get()) });
}, clearingSignalHost);
const beforeClearingSignal = clearingSignalRenders;
clearingSignalHost.onCommit = () => {
  clearingSignalHost.onCommit = null;
  clearingSignal.set(1);
};
check(clearingSignalRoot.close() === true && clearingSignalRenders === beforeClearingSignal
  && clearingSignalHost.diagnosticCount() === 0 && clearingSignalHost.snapshot().length === 0,
  'signal writes during host cleanup cannot reconcile against nodes being removed');

const customChromeHost = makeHost();
const customChromeRoot = UI.createRoot(() => UI.element('workspace', {
  width: 'fill', height: 'fill', children: UI.element('window', {
    id: 'custom-chrome', title: 'Custom chrome', x: 12.25, y: 8.5, width: 120, height: 80,
    titlebarHeight: 32, titlebarButtonWidth: 16, titlebarTitle: false,
    children: UI.element('column', { width: 'fill', height: 'fill',
      children: UI.element('label', { text: 'Clipped content', width: 'fill', height: 100 }) })
  })
}), customChromeHost, { viewport: { width: 200, height: 120 } });
const customChromeWindow = customChromeRoot.layout().nodes[0].children[0];
const customChromeBody = customChromeWindow.children[0];
check(customChromeWindow.rect.x === 12.25 && customChromeWindow.rect.y === 8.5
  && customChromeBody.rect.y === 40.5 && customChromeBody.rect.height === 48
  && customChromeBody.clip.y === 40.5 && customChromeBody.clip.height === 48
  && customChromeBody.children[0].clip.y === 40.5 && customChromeBody.children[0].clip.height === 48,
  'custom titlebar height controls the fractional body inset and inherited clipping');
customChromeRoot.close();

const responsiveWindowHost = makeHost();
let responsiveWindowMode = 'normal';
let responsiveBusinessRenders = 0;
function ResponsiveWindowBody() {
  responsiveBusinessRenders++;
  return UI.element('label', { text: 'Retained business content', height: 12 });
}
const responsiveWindowRoot = UI.createRoot(() => UI.element('workspace', {
  width: 'fill', height: 'fill', children: UI.element('window', {
    id: 'responsive-window', title: 'Responsive window', x: 16, y: 16, width: 240, height: 160,
    mode: responsiveWindowMode,
    responsiveRect: {
      x: { base: '10%', profiles: { 5: '90%' } },
      y: { base: '25%', profiles: { 5: '90%' } },
      width: { base: '50%', profiles: { 5: '25%' } },
      height: { base: '60%', profiles: { 5: '10%' } }
    },
    minWidth: 200, maxWidth: 300, minHeight: 80, maxHeight: 150,
    children: UI.element(ResponsiveWindowBody, {})
  })
}), responsiveWindowHost, { viewport: {
  width: 400, height: 300, safeArea: { left: 10, top: 6, right: 10, bottom: 14 }
} });
function responsiveWindowRect() {
  const rect = responsiveWindowRoot.layout().nodes[0].children[0].rect;
  return [rect.x, rect.y, rect.width, rect.height].join(',');
}
check(responsiveWindowRect() === '48,76,200,150',
  'responsive normal geometry resolves percentages against the safe workspace and numeric constraints');
const beforeResponsiveResize = responsiveBusinessRenders;
check(responsiveWindowRoot.resize({ width: 1280, height: 720,
  safeArea: { left: 10, top: 6, right: 10, bottom: 14 } }) === true
  && responsiveWindowRect() === '970,626,300,80' && responsiveBusinessRenders === beforeResponsiveResize,
  'viewport resize recomputes profile geometry and clamps origins without rerendering business content');
responsiveWindowMode = 'maximized';
check(responsiveWindowRoot.refresh() === true && responsiveWindowRect() === '10,6,1260,700',
  'maximized geometry fills the safe work area instead of normal constraints');
responsiveWindowMode = 'normal';
check(responsiveWindowRoot.refresh() === true && responsiveWindowRect() === '970,626,300,80',
  'restoring normal mode reapplies the retained responsive geometry');
const beforeTinyResize = responsiveBusinessRenders;
check(responsiveWindowRoot.resize({ width: 60, height: 35,
  safeArea: { left: 10, top: 6, right: 10, bottom: 14 } }) === true
  && responsiveWindowRect() === '10,6,40,15' && responsiveBusinessRenders === beforeTinyResize,
  'tiny work areas take precedence over normal minimum dimensions');
responsiveWindowRoot.close();

const rawWindowEventHost = makeHost();
let rawWindowOptIns = false;
let rawWindowMode = 'normal';
let rawWindowActive = true;
let rawWindowVisible = true;
let rawWindowModal = false;
let rawWindowOverrides = {};
let rawInvalidResizeChild = false;
const rawWindowEvents = [];
let rawResizePayload = null;
const rawWindowEventRoot = UI.createRoot(() => UI.element('workspace', {
  width: 'fill', height: 'fill', children: UI.element('window', {
    id: 'raw-events', title: 'Controlled events', x: 10, y: 10, width: 120, height: 80,
    active: rawWindowActive, visible: rawWindowVisible, mode: rawWindowMode,
    ...(rawWindowOptIns ? { resizable: true, minimizable: true, maximizable: true } : {}),
    onResize: event => { rawWindowEvents.push(event.type); rawResizePayload = event; },
    onMinimize: event => rawWindowEvents.push(event.type),
    onMaximize: event => rawWindowEvents.push(event.type),
    onRestore: event => rawWindowEvents.push(event.type),
    onMove: event => rawWindowEvents.push(event.type),
    ...rawWindowOverrides,
    children: rawInvalidResizeChild ? UI.element('button', { text: 'Invalid target', onResize: () => {} })
      : rawWindowModal ? UI.element('dialog', { id: 'raw-event-modal', children:
        UI.element('panel', { width: 60, height: 40 }) }) : null
  })
}), rawWindowEventHost, { viewport: { width: 320, height: 240 } });
const rawResizeInput = { x: 12, y: 14, width: 94, height: 52, edge: 'nw' };
check(rawWindowEventRoot.dispatch('raw-events', 'resize', rawResizeInput) === false
  && rawWindowEventRoot.dispatch('raw-events', 'minimize', {}) === false
  && rawWindowEventRoot.dispatch('raw-events', 'maximize', {}) === false,
  'raw resizing minimizing and maximizing are opt-in capabilities');
rawWindowOptIns = true;
check(rawWindowEventRoot.refresh() === true && rawWindowEventRoot.dispatch('raw-events', 'resize', rawResizeInput) === true
  && Object.isFrozen(rawResizePayload)
  && [rawResizePayload.x, rawResizePayload.y, rawResizePayload.width, rawResizePayload.height, rawResizePayload.edge].join(',') === '12,14,94,52,nw',
  'resize callbacks receive the frozen complete proposal including width height and edge');
check(rawWindowEventRoot.dispatch('raw-events', 'minimize', {}) === true
  && rawWindowEventRoot.dispatch('raw-events', 'maximize', {}) === true
  && rawWindowEventRoot.dispatch('raw-events', 'restore', {}) === false
  && rawWindowEventRoot.layout().nodes[0].children[0].rect.width === 120,
  'normal window action callbacks request state changes without automatically changing geometry');
rawWindowMode = 'maximized';
check(rawWindowEventRoot.refresh() === true
  && rawWindowEventRoot.dispatch('raw-events', 'resize', rawResizeInput) === false
  && rawWindowEventRoot.dispatch('raw-events', 'move', { x: 20, y: 20 }) === false
  && rawWindowEventRoot.dispatch('raw-events', 'maximize', {}) === false
  && rawWindowEventRoot.dispatch('raw-events', 'restore', {}) === true
  && rawWindowEvents.join(',') === 'resize,minimize,maximize,restore',
  'maximized windows allow restore but reject user move resize and repeated maximize');
rawWindowActive = false;
check(rawWindowEventRoot.refresh() === true && rawWindowEventRoot.dispatch('raw-events', 'restore', {}) === false,
  'inactive window chrome must activate before requesting restore');
rawWindowMode = 'normal';
check(rawWindowEventRoot.refresh() === true
  && rawWindowEventRoot.dispatch('raw-events', 'resize', rawResizeInput) === false
  && rawWindowEventRoot.dispatch('raw-events', 'minimize', {}) === false
  && rawWindowEventRoot.dispatch('raw-events', 'maximize', {}) === false,
  'inactive windows cannot request resize minimize or maximize');
rawWindowActive = true;
rawWindowVisible = false;
check(rawWindowEventRoot.refresh() === true
  && rawWindowEventRoot.dispatch('raw-events', 'resize', rawResizeInput) === false
  && rawWindowEventRoot.dispatch('raw-events', 'minimize', {}) === false,
  'hidden windows cannot dispatch chrome events');
rawWindowVisible = true;
rawWindowModal = true;
check(rawWindowEventRoot.refresh() === true
  && rawWindowEventRoot.dispatch('raw-events', 'resize', rawResizeInput) === false
  && rawWindowEventRoot.dispatch('raw-events', 'minimize', {}) === false
  && rawWindowEventRoot.dispatch('raw-events', 'maximize', {}) === false,
  'the global modal takes precedence over all workspace chrome events');
rawWindowModal = false;
check(rawWindowEventRoot.refresh() === true, 'raw event fixture returns to normal interaction');
const beforeMalformedResize = rawWindowEvents.length;
for (const malformed of [ { ...rawResizeInput, width: -1 }, { ...rawResizeInput, height: Infinity },
  { ...rawResizeInput, x: NaN }, { ...rawResizeInput, edge: 'north' }, { x: 0, y: 0, width: 40, height: 20 } ]) {
  check(rawWindowEventRoot.dispatch('raw-events', 'resize', malformed) === false, 'malformed resize proposals do not reach guest callbacks');
}
check(rawWindowEvents.length === beforeMalformedResize, 'invalid resize proposals have no callback side effects');
const strictWindowSnapshot = rawWindowEventRoot.layout();
const strictWindowCommits = rawWindowEventHost.counts().commits;
for (const invalid of [ { resizable: 'yes' }, { minimizable: null }, { maximizable: 1 },
  { titlebarHeight: 0.5 }, { titlebarButtonWidth: 0 }, { titlebarTitle: 'yes' },
  { responsiveRect: { width: { base: '50%', profiles: { 6: 'auto' } } } },
  { responsiveRect: { x: { profiles: { 7: 3 } } } }, { responsiveRect: { height: -1 } },
  { responsiveRect: { width: '0%' } }, { responsiveRect: { x: Infinity } },
  { responsiveRect: { x: { base: 0, unknown: 1 } } },
  { minWidth: '40' }, { maxWidth: 39 }, { minHeight: 19 }, { maxHeight: 10 }, { mode: 'minimized' } ]) {
  rawWindowOverrides = invalid;
  check(rawWindowEventRoot.refresh() === false && rawWindowEventRoot.layout() === strictWindowSnapshot
    && rawWindowEventHost.counts().commits === strictWindowCommits,
    'invalid window configuration including inactive responsive branches preserves the published candidate');
}
rawWindowOverrides = {};
rawInvalidResizeChild = true;
check(rawWindowEventRoot.refresh() === false && rawWindowEventRoot.layout() === strictWindowSnapshot,
  'new window action handlers are rejected on non-window primitives');
rawWindowEventRoot.close();

const hiddenLegacyHost = makeHost();
let hiddenLegacyClicks = 0;
const hiddenLegacyRoot = UI.createRoot(() => UI.element('panel', { visible: false,
  children: UI.element('button', { id: 'hidden-legacy-action', text: 'Hidden', onClick: () => hiddenLegacyClicks++ })
}), hiddenLegacyHost, { viewport: { width: 320, height: 240 } });
check(hiddenLegacyRoot.dispatch('hidden-legacy-action', 'click', {}) === false && hiddenLegacyClicks === 0,
  'hidden nonwindow descendants cannot receive explicit dispatch');
hiddenLegacyRoot.close();

const presentationHost = makeHost();
let presentationStack = false;
let presentationModal = false;
let presentationHidden = false;
let presentationInvalid = null;
let presentationClicks = 0;
let presentationRenders = 0;
const presentationRoot = UI.createRoot(() => {
  presentationRenders++;
  const action = UI.element('button', { id: 'presentation-action', width: 80, height: 20,
    text: 'Restore', onClick: () => presentationClicks++ });
  const panel = UI.element(presentationInvalid === 'wrong-type' ? 'button' : 'panel', {
    workspacePresentation: presentationInvalid === 'wrong-marker' ? 'yes' : true,
    width: presentationStack ? 90 : 'fill', height: 28, anchor: 'bottomRight',
    visible: !presentationHidden, children: action
  });
  if (presentationInvalid === 'root-marker') return panel;
  if (presentationInvalid === 'false-marker') return UI.element('panel', { workspacePresentation: false, children: action });
  const workspace = UI.element('workspace', { width: 'fill', height: 'fill', children:
    UI.element('window', { id: 'presentation-window', title: 'Presentation work area', x: 0, y: 0,
      width: 120, height: 80, mode: 'maximized', active: true, children: presentationModal
        ? UI.element('dialog', { id: 'presentation-modal', children: UI.element('panel', { width: 60, height: 40 }) }) : null }) });
  const presentationChildren = [workspace, panel];
  if (presentationInvalid === 'nested-marker') presentationChildren[1] = UI.element('panel', { height: 28, children: panel });
  if (presentationInvalid === 'no-workspace') presentationChildren.shift();
  const presentationScreenProps = { width: 'fill', height: 'fill', children: presentationChildren };
  if (presentationStack) presentationScreenProps.layout = 'stack';
  if (presentationInvalid === 'wrong-layout') presentationScreenProps.layout = 'row';
  return UI.element('screen', presentationScreenProps);
}, presentationHost, { viewport: { width: 320, height: 240 } });
let presentationLayout = presentationRoot.layout().nodes[0];
check(presentationLayout.children[0].rect.height === 212
  && presentationLayout.children[0].children[0].rect.height === 212
  && presentationLayout.children[1].rect.y === 212 && presentationLayout.children[1].rect.height === 28
  && presentationLayout.children[1].style.workspacePresentation === true,
  'ordinary screen column reserves the fixed taskbar before computing the maximized workspace work area');
check(presentationRoot.dispatch('presentation-action', 'click', {}) === true && presentationClicks === 1,
  'visible marked presentation controls accept coordinate-free explicit dispatch');
const beforePresentationResize = presentationRenders;
check(presentationRoot.resize({ width: 320, height: 18 }) === true
  && presentationRoot.layout().nodes[0].children[0].rect.height === 0
  && presentationRoot.layout().nodes[0].children[0].children[0].rect.height === 0
  && presentationRoot.layout().nodes[0].children[1].clip.height === 18
  && presentationRenders === beforePresentationResize,
  'a viewport shorter than the taskbar gives the workspace a nonnegative zero work area and clips the bar without rerendering');
check(presentationRoot.resize({ width: 320, height: 240 }) === true, 'taskbar geometry returns on ordinary resize');
presentationStack = true;
check(presentationRoot.refresh() === true, 'screen stack is an explicit opt-in');
presentationLayout = presentationRoot.layout().nodes[0];
check(presentationLayout.children[0].rect.width === 320 && presentationLayout.children[0].rect.height === 240
  && presentationLayout.children[1].rect.x === 230 && presentationLayout.children[1].rect.y === 212
  && presentationLayout.children[1].rect.width === 90 && presentationLayout.children[1].rect.height === 28,
  'screen stack overlays only the bounded anchored presentation without consuming the workspace work area');
presentationModal = true;
check(presentationRoot.refresh() === true && presentationRoot.dispatch('presentation-action', 'click', {}) === false
  && presentationClicks === 1, 'the global modal outranks marked presentation explicit dispatch');
presentationModal = false;
presentationHidden = true;
check(presentationRoot.refresh() === true && presentationRoot.dispatch('presentation-action', 'click', {}) === false,
  'hidden marked presentation descendants cannot receive explicit dispatch');
presentationHidden = false;
check(presentationRoot.refresh() === true, 'the marked presentation can be shown again');
const validPresentationSnapshot = presentationRoot.layout();
const validPresentationCommits = presentationHost.counts().commits;
for (const invalidPresentation of ['wrong-type', 'wrong-marker', 'root-marker', 'no-workspace', 'nested-marker', 'wrong-layout']) {
  presentationInvalid = invalidPresentation;
  check(presentationRoot.refresh() === false && presentationRoot.layout() === validPresentationSnapshot
    && presentationHost.counts().commits === validPresentationCommits,
    'invalid marker placement type or screen layout preserves the published workspace candidate');
}
presentationInvalid = 'false-marker';
check(presentationRoot.refresh() === true && presentationRoot.dispatch('presentation-action', 'click', {}) === true,
  'false workspace presentation markers leave ordinary legacy panels unrestricted');
presentationRoot.close();

const intrinsicScreenHost = makeHost();
let intrinsicScreenStacked = false;
const intrinsicScreenRoot = UI.createRoot(() => {
  const intrinsicScreenProps = { width: 200, height: 'auto', children: [
    UI.element('panel', { width: 50, height: 100 }),
    UI.element('panel', { width: 20, height: 20, anchor: 'bottomRight' })
  ] };
  if (intrinsicScreenStacked) intrinsicScreenProps.layout = 'stack';
  return UI.element('screen', intrinsicScreenProps);
}, intrinsicScreenHost, { viewport: { width: 320, height: 240 } });
check(intrinsicScreenRoot.layout().nodes[0].rect.height === 120,
  'auto-sized screens retain the legacy column intrinsic height without opt-in stack layout');
intrinsicScreenStacked = true;
check(intrinsicScreenRoot.refresh() === true && intrinsicScreenRoot.layout().nodes[0].rect.height === 100
  && intrinsicScreenRoot.layout().nodes[0].children[1].rect.x === 180
  && intrinsicScreenRoot.layout().nodes[0].children[1].rect.y === 80,
  'auto-sized opt-in stack screens use maximum child extents and anchor within that same intrinsic rectangle');
intrinsicScreenRoot.close();

export const uiCoreProof = Object.freeze({
  primitives: UI.primitives().length,
  profiles: selectedProfiles.join(','),
  keyedOrder: 'b,a,c',
  diagnostics: 'render,component,layout,host-update,event',
  visualProps: visualImage.props.opacity === 0.25 && visualPanel.props.opacity === 0.5
    && truncateLabel.props.truncate === true,
  disposed: root.isDisposed(),
  passed: true
});
