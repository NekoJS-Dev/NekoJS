import { UI } from 'nekojs/jsx-runtime';

let opened = false;
let host, root;
const value = UI.createSignal('');
const releases = UI.createSignal(0);
const clicks = UI.createSignal(0);

function InteractionScreen() {
  return UI.element('screen', {
    id: 'ticket41-screen', closeOnEscape: true,
    children: UI.element('column', {
      id: 'ticket41-panel', width: 320, height: 420, padding: 8, gap: 6,
      children: [
        UI.element('label', { id: 'title', text: 'Ticket 41 selection and capture' }),
        UI.element('input', {
          id: 'name', width: 260, value: value.get(), maxLength: 12,
          placeholder: 'Type Neko, then select',
          onChange: event => {
            value.set(event.value == null ? '' : event.value);
            console.info('[ticket41-final] change=' + event.value);
          }
        }),
        UI.element('button', {
          id: 'capture', width: 260, text: 'Hold, move outside, release',
          onClick: () => {
            clicks.set(clicks.get() + 1);
            console.info('[ticket41-final] click=' + clicks.get());
          },
          onRelease: () => {
            releases.set(releases.get() + 1);
            console.info('[ticket41-final] release=' + releases.get());
          },
          tooltip: 'Mouse release stays with the pressed control'
        }),
        UI.element('label', { id: 'counts', text: 'Clicks: ' + clicks.get() + ' Releases: ' + releases.get() }),
        UI.element('button', {
          id: 'disabled', width: 260, text: 'Disabled', disabled: true,
          onClick: () => console.info('[ticket41-final] ERROR disabled clicked')
        }),
        UI.element('scroll', {
          id: 'scroll', width: 260, height: 100,
          children: Array.from({ length: 12 }, (_, index) => UI.element('label', {
            id: 'line-' + index, text: 'Scroll line ' + (index + 1), height: 20
          }))
        })
      ]
    })
  });
}

ClientEvents.tickPost(() => {
  if (opened) return;
  opened = true;
  host = ClientUI.screen('Ticket 41 final interaction', false);
  root = UI.createRoot(InteractionScreen, host, { id: 'ticket41-final-root' });
  host.bindRoot(root);
  host.open();
  console.info('[ticket41-final] opened');
});

export { host, root };
