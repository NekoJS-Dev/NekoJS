import { UI } from 'nekojs/jsx-runtime';

let opened = false;
let firstHost, firstRoot, secondHost, secondRoot;
const healthy = UI.createSignal(0);
const allowEscape = UI.createSignal(false);

function verifyReplacement() {
  const disposed = firstRoot.isDisposed();
  const staleEvent = firstRoot.dispatch('healthy', 'click', {});
  console.info('[ticket41-lifecycle] oldDisposed=' + disposed + ' staleDispatch=' + staleEvent);
}

function replaceScreen() {
  secondHost = ClientUI.screen('Ticket 41 replacement', true);
  secondRoot = UI.createRoot(() => UI.element('screen', {
    id: 'second-screen', closeOnEscape: allowEscape.get(),
    children: UI.element('column', {
      id: 'second-panel', width: 300, height: 240, padding: 8, gap: 6,
      children: [
        UI.element('label', { id: 'replacement-title', text: 'Replacement Screen' }),
        UI.element('button', {
          id: 'verify', text: 'Verify old root cleanup', width: 260,
          onClick: verifyReplacement
        }),
        UI.element('button', {
          id: 'allow-escape', text: 'Allow Escape', width: 260,
          onClick: () => {
            allowEscape.set(true);
            console.info('[ticket41-lifecycle] escape enabled');
          }
        })
      ]
    })
  }), secondHost, { id: 'ticket41-second-root' });
  secondHost.bindRoot(secondRoot);
  secondHost.open();
  console.info('[ticket41-lifecycle] replacement opened');
  verifyReplacement();
}

function FirstScreen() {
  return UI.element('screen', {
    id: 'first-screen', closeOnEscape: true,
    children: UI.element('column', {
      id: 'first-panel', width: 300, height: 260, padding: 8, gap: 6,
      children: [
        UI.element('label', { id: 'title', text: 'Lifecycle and error retention' }),
        UI.element('button', {
          id: 'throw-event', text: 'Throw event error', width: 260,
          onClick: () => {
            console.info('[ticket41-lifecycle] throwing intentional event error');
            throw new Error('ticket41 intentional event failure');
          }
        }),
        UI.element('button', {
          id: 'healthy', text: 'Healthy after error', width: 260,
          onClick: event => {
            healthy.set(healthy.get() + 1);
            console.info('[ticket41-lifecycle] healthy=' + healthy.get()
              + ' frozen=' + Object.isFrozen(event) + ' target=' + event.target);
          }
        }),
        UI.element('label', { id: 'healthy-count', text: 'Healthy count: ' + healthy.get() }),
        UI.element('button', { id: 'replace', text: 'Replace Screen', width: 260, onClick: replaceScreen })
      ]
    })
  });
}

ClientEvents.tickPost(() => {
  if (opened) return;
  opened = true;
  firstHost = ClientUI.screen('Ticket 41 lifecycle', false);
  firstRoot = UI.createRoot(FirstScreen, firstHost, { id: 'ticket41-first-root' });
  firstHost.bindRoot(firstRoot);
  firstHost.open();
  console.info('[ticket41-lifecycle] first opened');
});

export { firstRoot, secondRoot };
