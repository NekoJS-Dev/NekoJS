import { UI } from 'nekojs/jsx-runtime';

let opened = false;
let host;
let root;
const value = UI.createSignal('Neko narration');
const visible = UI.createSignal(true);
const clicks = UI.createSignal(0);

function AccessibilityScreen() {
  return UI.element('screen', { id: 'round4-accessibility', closeOnEscape: true,
    children: UI.element('column', { id: 'content', width: 340, height: 220, padding: 8, spacing: 8,
      children: [
        UI.element('label', { id: 'heading', text: 'Tooltip, narration and hidden input', height: 14 }),
        UI.element('button', { id: 'healthy', text: 'Healthy button', width: 250, height: 22,
          tooltip: 'Healthy tooltip', onClick: () => {
            clicks.set(clicks.get() + 1);
            console.info('[round4-a11y] healthy=' + clicks.get());
          } }),
        UI.element('button', { id: 'disabled', text: 'Disabled button', disabled: true, width: 250, height: 22,
          tooltip: 'Disabled tooltip', onClick: () => console.info('[round4-a11y] unexpected disabled callback') }),
        UI.element('column', { id: 'editor-parent', visible: visible.get(), width: 270, height: 28,
          children: UI.element('input', { id: 'editor', value: value.get(), width: 250, height: 24,
            onChange: event => {
              value.set(event.value);
              console.info('[round4-a11y] value=' + event.value);
            } }) }),
        UI.element('button', { id: 'hide', text: 'Hide / show input container', width: 250, height: 22,
          onClick: () => {
            visible.set(!visible.get());
            console.info('[round4-a11y] editor-visible=' + visible.get());
          } }),
        UI.element('label', { id: 'help', text: 'Ctrl+B enables UI narration; Tab and Shift+Tab move focus.', width: 300, height: 26 })
      ]
    })
  });
}

ClientEvents.tickPost(() => {
  if (opened) return;
  opened = true;
  host = ClientUI.screen('Round 4 accessibility acceptance', false);
  root = UI.createRoot(AccessibilityScreen, host, { id: 'round4-a11y-root' });
  host.bindRoot(root);
  host.open();
  console.info('[round4-a11y] opened');
});

export { host, root };
