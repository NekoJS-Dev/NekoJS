import { UI } from 'nekojs/jsx-runtime';
// Ticket 47 example-verification fixture. Every primitive minimal example from
// docs/architecture-refactor/ai-authoring-contract.md section 5 (check ids
// screen-1 .. spacer-1), the shared prop semantics, and both ticket-46 conversion
// outputs from docs/ui-conversion/fixtures/ run here against a fake host.
// Executor: TypeScriptUiAuthoringDocsTest.
import { renderLoginForm, username, password, error, submitting, lastSubmitted } from './login-form.output.tsx';
import { renderCardGrid, catalog, selected } from './card-grid.output.tsx';

function check(condition, message) {
  if (!condition) throw new Error('authoring docs proof failed: ' + message);
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
    isOwnerThread: () => owner,
    enqueue: action => {
      if (!acceptingQueue) return false;
      queue.push(action);
      return true;
    },
    supportsPrimitive: type => UI.primitives().includes(type),
    measureText: (text, fontSize, maxWidth) => ({
      width: Math.min(maxWidth, String(text == null ? '' : text).length * Math.ceil(fontSize * 0.6)),
      height: Math.ceil(fontSize)
    }),
    layout: (tree, viewport, snapshot) => {
      counts.layouts++;
      if (snapshot != null) layoutSnapshot = snapshot;
    },
    reportDiagnostic: diagnostic => diagnostics.push(diagnostic),
    begin: () => {
      const staged = new Map(Array.from(nodes, ([id, node]) => [id, {
        id: node.id, type: node.type, key: node.key, props: { ...node.props }, children: node.children.slice()
      }]));
      let stagedRoots = roots.slice();
      return {
        create: (type, key, props) => {
          const id = nextId++;
          staged.set(id, { id, type, key, props: { ...props }, children: [] });
          return id;
        },
        update: (id, type, key, props) => {
          const node = staged.get(id);
          check(node != null, 'updates target staged host nodes');
          node.type = type; node.key = key; node.props = { ...props };
        },
        order: (parent, children) => {
          if (parent == null) stagedRoots = children.slice();
          else staged.get(parent).children = children.slice();
        },
        remove: id => staged.delete(id),
        commit: nextRoots => {
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

function findNodeById(snapshot, id) {
  return findIn(snapshot.nodes, id);
}
function findIn(nodes, id) {
  for (const node of nodes) {
    if (node.id === id) return node;
    const nested = findIn(node.children, id);
    if (nested != null) return nested;
  }
  return null;
}

// ---- Section 5 catalog examples ----

check(UI.primitives().join(',') === 'screen,panel,row,column,stack,scroll,label,button,input,image,spacer',
  'primitive registry is complete (catalog-registry)');

const clicked = UI.createSignal(false);
const lastTarget = UI.createSignal('');
const name = UI.createSignal('');
const lines = ['a', 'b', 'c', 'd', 'e'];

function renderCatalog() {
  return (
    <screen id="catalog-screen" title="Catalog" pausesGame={false} closeOnEscape={true}>
      <column id="catalog-body" width="100%" height="100%" padding={8} gap={4}>
        <panel id="catalog-card" width={120} padding={8} background="#F0F0F080"
               borderColor="navy" borderWidth={1} radius={4}>
          <label id="card-title" fontSize={10}>{'Panel'}</label>
        </panel>
        <row id="catalog-actions" gap={8} justify="center">
          <button id="catalog-ok" onClick={() => clicked.set(true)}>{'OK'}</button>
          <button id="catalog-cancel">{'Cancel'}</button>
        </row>
        <stack id="catalog-overlay" width={100} height={50}>
          <label id="catalog-overlay-base">{'base'}</label>
          <spacer id="catalog-overlay-pin" width={20} height={10} anchor="bottomRight" />
        </stack>
        <scroll id="catalog-scroll" width={100} height={30} scrollOffset={0}>
          <column id="catalog-scroll-content" gap={2}>
            {lines.map(line => <label key={line} id={'catalog-line-' + line} fontSize={8}>{line}</label>)}
          </column>
        </scroll>
        <label id="catalog-hello" color="#FFFF00" fontSize={{ base: 9, profiles: { 6: 12 } }}>{'Hello'}</label>
        <button id="catalog-click" disabled={false}
                onClick={event => lastTarget.set(event.target)}>{'Click'}</button>
        <input id="catalog-name" value={name.get()} placeholder="name" maxLength={32}
               onChange={event => name.set(event.value == null ? '' : event.value)} />
        <image id="catalog-art" resource="minecraft:block/stone" fit="contain" width={16} height={16} />
        <row id="catalog-spaced" width="100%">
          <label id="catalog-left">{'L'}</label>
          <spacer id="catalog-flex-gap" width="fill" />
          <label id="catalog-right">{'R'}</label>
        </row>
      </column>
    </screen>
  );
}

const host = makeHost();
const root = UI.createRoot(renderCatalog, host, { id: 'authoring-catalog-root' });

check(host.snapshot()[0].type === 'screen' && host.snapshot()[0].props.title === 'Catalog',
  'screen-1: the screen example mounts as the root with its title');
const card = findNodeById(host.layoutSnapshot(), 'catalog-card');
check(card != null && card.style.background === '#F0F0F080' && card.style.radius === 4,
  'panel-1: panel visual props resolve onto the layout style');
const ok = findNodeById(host.layoutSnapshot(), 'catalog-ok');
const cancel = findNodeById(host.layoutSnapshot(), 'catalog-cancel');
check(ok != null && cancel != null && cancel.rect.x > ok.rect.x + ok.rect.width,
  'row-1: row children lay out horizontally in order');
const line1 = findNodeById(host.layoutSnapshot(), 'catalog-line-a');
const line2 = findNodeById(host.layoutSnapshot(), 'catalog-line-b');
check(line1 != null && line2 != null && line2.rect.y > line1.rect.y,
  'column-1: column children lay out vertically in order');
const overlay = findNodeById(host.layoutSnapshot(), 'catalog-overlay');
const pin = findNodeById(host.layoutSnapshot(), 'catalog-overlay-pin');
check(overlay != null && pin != null && pin.rect.x > overlay.rect.x && pin.rect.y > overlay.rect.y,
  'stack-1: anchor bottomRight places the child inside the stack');
check(host.layoutSnapshot().diagnostics.some(entry => String(entry).startsWith('catalog-scroll-content:overflow-')),
  'scroll-1: overflowing scroll content reports an overflow diagnostic');
const hello = findNodeById(host.layoutSnapshot(), 'catalog-hello');
check(hello != null && hello.style.fontSize === 9, 'label-1: base profile resolves fontSize 9');
const art = findNodeById(host.layoutSnapshot(), 'catalog-art');
check(art != null && art.style.resource === 'minecraft:block/stone',
  'image-1: image mounts with its resource id passed through to the host');
const flexGap = findNodeById(host.layoutSnapshot(), 'catalog-flex-gap');
const leftLabel = findNodeById(host.layoutSnapshot(), 'catalog-left');
check(flexGap != null && leftLabel != null && flexGap.rect.width > 0 && flexGap.rect.x > leftLabel.rect.x,
  'spacer-1: fill spacer takes the remaining row width');
check(root.dispatch('catalog-click', 'click', {}) === true && lastTarget.get() === 'catalog-click',
  'button-1: onClick receives the frozen event with target id');
check(root.dispatch('catalog-name', 'change', { value: 'ai' }) === true && name.get() === 'ai',
  'input-1: controlled input round-trips through onChange');

// Shared prop semantics across profiles
check(root.resize({ width: 1280, height: 720 }) === true && host.layoutSnapshot().profile === 6,
  'shared-profile: resize reaches profile six');
check(findNodeById(host.layoutSnapshot(), 'catalog-hello').style.fontSize === 12,
  'shared-profile: the profile six fontSize override resolves');

// Failure phases keep the last committed tree (contract sections 2 and 9)
const failureHost = makeHost();
const failureMode = UI.createSignal('good');
function failingRender() {
  const badProps = failureMode.get() === 'unknown-prop' ? { unsupported: 1 } : {};
  return UI.element('screen', { id: 'failing-screen', children: UI.element('label', Object.assign({ id: 'failing-label' }, badProps), 'x') });
}
const failureRoot = UI.createRoot(failingRender, failureHost, { id: 'authoring-failure-root' });
const failureBefore = JSON.stringify(failureHost.snapshot());
failureMode.set('unknown-prop');
check(failureHost.lastPhase() === 'layout' && JSON.stringify(failureHost.snapshot()) === failureBefore,
  'selfcheck-unknown-prop: unsupported prop fails the layout phase and keeps the active tree');

// Thread discipline: off-owner-thread writes queue
host.setOwner(false);
check(name.set('queued') === 'queued' && name.get() === 'ai',
  'thread-queue: off-thread signal writes report queueing without early mutation');
host.flush();
check(name.get() === 'queued', 'thread-queue: queued writes apply on the owner thread');

// ---- docs/ui-conversion fixture outputs (ticket 46) ----

const loginHost = makeHost();
const loginRoot = UI.createRoot(renderLoginForm, loginHost, { id: 'login-fixture-root' });
check(loginHost.findById('login-username') != null && loginHost.findById('login-submit') != null,
  'login-fixture: the converted form mounts');
check(findNodeById(loginHost.layoutSnapshot(), 'login-card').style.background === '#F5F5F5'
  && findNodeById(loginHost.layoutSnapshot(), 'login-title').style.color === '#212529'
  && findNodeById(loginHost.layoutSnapshot(), 'login-username-label').style.color === '#212529',
  'login-fixture: opaque RGB colors and dark field labels match the light card');
const usernameRect = findNodeById(loginHost.layoutSnapshot(), 'login-username').rect;
const passwordRect = findNodeById(loginHost.layoutSnapshot(), 'login-password').rect;
const submitRect = findNodeById(loginHost.layoutSnapshot(), 'login-submit').rect;
check(usernameRect.width === 196 && passwordRect.width === 196
  && usernameRect.height === 20 && passwordRect.height === 20,
  'login-fixture: both inputs fill the card inner width with usable native height');
check(submitRect.y >= passwordRect.y + passwordRect.height,
  'login-fixture: actions do not overlap the password input');
check(findNodeById(loginHost.layoutSnapshot(), 'login-submit').clip.height === 20,
  'login-fixture: the submit button is not clipped by the card');
check(findNodeById(loginHost.layoutSnapshot(), 'login-error').visible === false,
  'login-fixture: the error label starts hidden');
check(loginRoot.dispatch('login-submit', 'click', {}) === true && error.get() !== '',
  'login-fixture: empty submit sets the error state');
check(findNodeById(loginHost.layoutSnapshot(), 'login-error').visible === true,
  'login-fixture: the error label shows while the error signal is set');
check(loginRoot.dispatch('login-username', 'change', { value: 'ai' }) === true && username.get() === 'ai',
  'login-fixture: username input round-trips');
loginRoot.dispatch('login-password', 'change', { value: 'secret' });
check(password.get() === 'secret', 'login-fixture: password input round-trips');
loginRoot.dispatch('login-submit', 'click', {});
check(submitting.get() === true && lastSubmitted.get() === 'ai' && error.get() === '',
  'login-fixture: complete credentials submit and clear the error');
check(findNodeById(loginHost.layoutSnapshot(), 'login-error').visible === false,
  'login-fixture: the error label hides again after a valid submit');
loginRoot.dispatch('login-cancel', 'click', {});
check(username.get() === '' && submitting.get() === false,
  'login-fixture: cancel resets the form state');
check(findNodeById(loginHost.layoutSnapshot(), 'login-title').style.fontSize === 10,
  'login-fixture: the default profile resolves the base title size');
check(loginRoot.resize({ width: 1280, height: 720 }) === true
  && findNodeById(loginHost.layoutSnapshot(), 'login-title').style.fontSize === 12,
  'login-fixture: profile six applies the title fontSize override');

const gridHost = makeHost();
const gridRoot = UI.createRoot(renderCardGrid, gridHost, { id: 'grid-fixture-root' });
check(gridHost.findById('card-p1') != null && gridHost.findById('card-p6') != null,
  'grid-fixture: all six converted cards mount');
check(gridRoot.dispatch('card-buy-p3', 'click', {}) === true && selected.get() === 'p3',
  'grid-fixture: the buy button drives the selected signal');
check(findNodeById(gridHost.layoutSnapshot(), 'card-p3').style.background === '#E6F0FFFF',
  'grid-fixture: selection restyles the card through the signal');
const p1Before = gridHost.findById('card-p1');
const reordered = catalog.get('products').slice();
const first = reordered[0];
reordered[0] = reordered[1];
reordered[1] = first;
catalog.set('products', reordered);
check(gridHost.findById('card-p1') === p1Before,
  'grid-fixture: keyed cards retain host identity across a store reorder');
check(gridRoot.resize({ width: 854, height: 480 }) === true
  && findNodeById(gridHost.layoutSnapshot(), 'grid-title').style.fontSize === 10,
  'grid-fixture: profile five keeps the base card title size');
check(gridRoot.resize({ width: 1280, height: 720 }) === true
  && findNodeById(gridHost.layoutSnapshot(), 'grid-title').style.fontSize === 12,
  'grid-fixture: profile six applies the grid title override');

root.close();
failureRoot.close();
loginRoot.close();
gridRoot.close();

export const authoringDocsProof = Object.freeze({
  passed: true,
  primitives: UI.primitives().length,
  fixtures: 'catalog,login-form,card-grid',
  rootsClosed: root.isDisposed() && failureRoot.isDisposed() && loginRoot.isDisposed() && gridRoot.isDisposed()
});
