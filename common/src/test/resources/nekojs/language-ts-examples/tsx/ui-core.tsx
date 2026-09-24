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
check(UI.primitives().join(',') === 'screen,panel,row,column,stack,scroll,label,button,input,image,spacer', 'primitive registry is complete');
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
