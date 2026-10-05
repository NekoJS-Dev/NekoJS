import { UI } from 'nekojs/jsx-runtime';

let opened = false;
let host;
let root;
let renderCount = 0;
const input = UI.createSignal('Neko mouse selection');

function TrialScreen() {
  renderCount++;
  return UI.element('screen', {
    id: 'round2-screen', closeOnEscape: true,
    children: UI.element('column', {
      id: 'trial-content', width: 340, height: 235, padding: 8, spacing: 6,
      children: [
        UI.element('label', { id: 'title', text: 'Mouse selection, rounded panels and resource repair', height: 14 }),
        UI.element('input', { id: 'editor', value: input.get(), width: 250, height: 24,
          onChange: event => {
            input.set(event.value);
            console.info('[round2] input=' + event.value);
          } }),
        UI.element('label', { id: 'selection-help', text: 'Click to place caret; drag or Shift+click to select', height: 12 }),
        UI.element('row', { id: 'panels', width: 320, height: 64, spacing: 8,
          children: [
            UI.element('panel', { id: 'square', width: 96, height: 64, background: '#2E4057',
              borderColor: '#FFFFFF', borderWidth: 4, radius: 0 }),
            UI.element('panel', { id: 'rounded', width: 96, height: 64, background: '#2E4057',
              borderColor: '#FFFFFF', borderWidth: 4, radius: 16 }),
            UI.element('panel', { id: 'transparent', width: 96, height: 64, background: '#2E4057',
              borderColor: '#FFFFFF', borderWidth: 4, radius: 16, opacity: 0.5 })
          ] }),
        UI.element('row', { id: 'resources', width: 320, height: 32, spacing: 10,
          children: [
            UI.element('image', { id: 'repair', resource: 'ticket44_round2:gui/repair', width: 32, height: 32, fit: 'stretch' }),
            UI.element('label', { id: 'repair-help', text: 'Resource starts corrupt. Reload packs after repair.', width: 250, height: 26 })
          ] }),
        UI.element('button', { id: 'inspect', text: 'Check repaired resource', width: 220, height: 22,
          onClick: () => {
            console.info('[round2] inspect=' + host.inspect());
            console.info('[round2] healthy click renderCount=' + renderCount);
          } })
      ]
    })
  });
}

ClientEvents.tickPost(() => {
  if (opened) return;
  opened = true;
  host = ClientUI.screen('Round 2 JSX acceptance', false);
  root = UI.createRoot(TrialScreen, host, { id: 'round2-root' });
  host.bindRoot(root);
  host.open();
  console.info('[round2] screen opened renderCount=' + renderCount);
});

export { host, root };
