import { UI } from 'nekojs/jsx-runtime';
import { renderLoginForm, username, password, error, submitting, lastSubmitted } from './login-form.output.tsx';

let host;
let root;
let ticks = 0;
let opened = false;
let closedLogged = false;
const start = Date.now();
const renderFault = UI.createSignal(false);
const recoveryCount = UI.createSignal(0);
const listItems = UI.createSignal([{ id: 'first', text: 'A' }]);
let lastListStage = 0;
const profiles = [
  { width: 100, height: 100 }, { width: 320, height: 180 },
  { width: 480, height: 240 }, { width: 640, height: 360 },
  { width: 854, height: 480 }, { width: 1280, height: 720 }
];

function find(nodes, id) {
  for (const node of nodes) {
    if (node.id === id) return node;
    const child = find(node.children, id);
    if (child) return child;
  }
  return null;
}

function renderCombined() {
  if (renderFault.get()) throw new Error('ticket48 deliberate render failure');
  const login = renderLoginForm();
  return UI.element('screen', { ...login.props, children: [
    ...login.children,
    UI.element('label', { id: 'pack-font', text: 'AAAA', font: 'ticket48_auto:probe', height: 16 }),
    UI.element('row', { id: 'failure-controls', width: 220, height: 24, gap: 2, children: [
      UI.element('button', { id: 'event-failure', text: 'Event fail', width: 70, height: 20,
        onClick: () => { throw new Error('ticket48 deliberate event failure'); } }),
      UI.element('button', { id: 'render-failure', text: 'Render fail', width: 70, height: 20,
        onClick: () => renderFault.set(true) }),
      UI.element('button', { id: 'recover', text: 'Recover ' + recoveryCount.get(), width: 76, height: 20,
        onClick: () => {
          renderFault.set(false);
          const count = recoveryCount.get() + 1;
          recoveryCount.set(count);
          if (count === 1) listItems.set([{ id: 'first', text: 'A' }, { id: 'second', text: 'B' }]);
          if (count === 2) listItems.set([{ id: 'first', text: 'AX' }, { id: 'second', text: 'B' }]);
          if (count === 3) listItems.set([{ id: 'second', text: 'B' }]);
        } })
    ] }),
    UI.element('row', { id: 'list-proof', width: 220, height: 10, children:
      listItems.get().map(entry => UI.element('label', {
        id: 'list-' + entry.id, key: entry.id, text: entry.text, width: 30, height: 10
      })) })
  ] });
}

function report(stage) {
  const counters = host.performanceCounters();
  const statistics = {};
  for (const name of ['initialBuild', 'layout', 'reconcile', 'resize', 'paint', 'diagnostics', 'cleanup']) {
    statistics[name] = counters.get(name);
  }
  const disposed = root.isDisposed();
  const layout = disposed ? null : root.layout();
  console.info('[ticket48-auto] ' + JSON.stringify({
    stage, ticks, elapsedMs: Date.now() - start, statistics, disposed,
    username: username.get(), passwordLength: password.get().length,
    error: error.get(), submitting: submitting.get(), lastSubmitted: lastSubmitted.get(),
    renderFault: renderFault.get(), recoveryCount: recoveryCount.get(),
    listValues: listItems.get(),
    listNodes: layout ? ['list-first', 'list-second'].flatMap(id => {
      const node = find(layout.nodes, id);
      return node ? [{ id: node.id, text: node.text ?? node.style?.text ?? null, rect: node.rect }] : [];
    }) : [],
    profile: layout ? layout.profile : null,
    packFontWidth: layout ? find(layout.nodes, 'pack-font').rect.width : null,
    usernameRect: layout ? find(layout.nodes, 'login-username').rect : null,
    passwordRect: layout ? find(layout.nodes, 'login-password').rect : null,
    submitRect: layout ? find(layout.nodes, 'login-submit').rect : null
  }));
}

ClientEvents.tickPost(() => {
  ticks++;
  if (!opened && ticks >= 120) {
    opened = true;
    host = ClientUI.screen('Ticket 48 converted login', false);
    root = UI.createRoot(renderCombined, host, { id: 'ticket48-auto-root' });
    host.bindRoot(root);
    host.open();
    report('open');
    for (let index = 0; index < profiles.length; index++) {
      const result = root.resize(profiles[index]);
      report('profile-' + (index + 1) + '-result-' + result);
    }
    root.resize(host.viewport());
    report('physical-window-restored');
  }
  if (!opened) return;
  if (!root.isDisposed() && recoveryCount.get() !== lastListStage) {
    lastListStage = recoveryCount.get();
    report('list-phase-' + lastListStage);
  }
  if (root.isDisposed() && !closedLogged) {
    closedLogged = true;
    report('closed');
  } else if (!root.isDisposed() && ticks % 100 === 0) {
    report('sample');
  }
});

export { host, root };
