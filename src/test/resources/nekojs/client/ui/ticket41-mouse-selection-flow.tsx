import { UI } from 'nekojs/jsx-runtime';

let opened = false;
let host, root;
let ticks = 0;
let remaining = 0;
let action = '';
const value = UI.createSignal('Wi😀Z');
const disabled = UI.createSignal(false);
const replacement = UI.createSignal(0);
const pulse = UI.createSignal(0);
const status = UI.createSignal('Ready');

function arm(nextAction) {
  action = nextAction;
  remaining = 100;
  status.set(nextAction + ' in 5 seconds: hold a selection drag');
  console.info('[ticket41-mouse] armed=' + action);
}

function reset() {
  remaining = 0;
  action = '';
  disabled.set(false);
  value.set('Wi😀Z');
  status.set('Reset: Wi😀Z');
  console.info('[ticket41-mouse] reset');
}

function MouseSelectionScreen() {
  return UI.element('screen', {
    id: 'ticket41-mouse-screen', closeOnEscape: true,
    children: UI.element('column', {
      id: 'mouse-panel', width: 400, height: 224, padding: 8, gap: 6,
      children: [
        UI.element('label', { id: 'title', text: 'Ticket 41 mouse selection' }),
        UI.element('label', { id: 'instruction', text: 'Click between W/i; type X => WXi😀Z' }),
        UI.element('input', {
          id: 'mouse-field', key: 'field-' + replacement.get(),
          width: 280, height: 24, value: value.get(), disabled: disabled.get(),
          maxLength: 24,
          onChange: event => {
            value.set(event.value == null ? '' : event.value);
            console.info('[ticket41-mouse] change=' + event.value);
          },
          onRelease: event => console.info('[ticket41-mouse] release button=' + event.button)
        }),
        UI.element('label', { id: 'current', text: 'Value: ' + value.get() }),
        UI.element('label', { id: 'pulse', text: 'Retained rerender pulse: ' + pulse.get() }),
        UI.element('label', { id: 'status', text: status.get() }),
        UI.element('button', { id: 'reset', width: 280, text: 'Reset / Enable', onClick: reset }),
        UI.element('button', {
          id: 'disable-later', width: 280, text: 'Disable field in 5 seconds',
          onClick: () => arm('disable')
        }),
        UI.element('button', {
          id: 'replace-later', width: 280, text: 'Replace field in 5 seconds',
          onClick: () => arm('replace')
        })
      ]
    })
  });
}

ClientEvents.tickPost(() => {
  if (!opened) {
    opened = true;
    host = ClientUI.screen('Ticket 41 native mouse selection', false);
    root = UI.createRoot(MouseSelectionScreen, host, { id: 'ticket41-mouse-root' });
    host.bindRoot(root);
    host.open();
    console.info('[ticket41-mouse] opened');
    return;
  }
  ticks++;
  if (root.isDisposed()) return;
  if (ticks % 40 === 0) pulse.set(pulse.get() + 1);
  if (remaining <= 0) return;
  remaining--;
  if (remaining > 0) return;
  if (action === 'disable') {
    disabled.set(true);
    status.set('Field disabled; release must not continue old drag');
  } else if (action === 'replace') {
    replacement.set(replacement.get() + 1);
    value.set('Fresh');
    status.set('Same id, new key: Fresh must not inherit old capture');
  }
  console.info('[ticket41-mouse] action=' + action + ' applied');
  action = '';
});

export { host, root };
