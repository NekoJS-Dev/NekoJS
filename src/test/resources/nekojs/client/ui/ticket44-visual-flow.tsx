import { UI } from 'nekojs/jsx-runtime';

let opened = false;
let host;
let root;

function VisualScreen() {
  return UI.element('screen', {
    id: 'ticket44-screen', closeOnEscape: true,
    children: UI.element('column', {
      id: 'visual-panel', width: 350, height: 230, padding: 8, spacing: 6,
      children: [
        UI.element('label', { id: 'large-title', text: 'Texture and font proof', fontSize: 18, height: 22 }),
        UI.element('label', { id: 'small-title', text: 'Small text at font size 9', fontSize: 9, height: 12 }),
        UI.element('label', { id: 'wrapped', text: 'Line one\nLine two', fontSize: 9, width: 220, height: 24 }),
        UI.element('row', {
          id: 'images', width: 320, height: 64, spacing: 10,
          children: [
            UI.element('image', { id: 'stone', resource: 'minecraft:block/stone', width: 64, height: 64, fit: 'contain' }),
            UI.element('image', { id: 'crop', resource: 'minecraft:block/stone', width: 64, height: 64,
              crop: { x: 0, y: 0, width: 8, height: 8 }, fit: 'stretch', opacity: 0.5 }),
            UI.element('image', { id: 'icon', icon: 'minecraft:item/paper', width: 64, height: 64, fit: 'contain' })
          ]
        }),
        UI.element('row', {
          id: 'failures', width: 320, height: 32, spacing: 10,
          children: [
            UI.element('image', { id: 'missing', resource: 'ticket44_trial:gui/missing', width: 32, height: 32 }),
            UI.element('image', { id: 'corrupt', resource: 'ticket44_trial:gui/corrupt', width: 32, height: 32 })
          ]
        }),
        UI.element('button', { id: 'alive', text: 'UI remains usable', width: 220, height: 20,
          onClick: () => console.info('[ticket44] healthy click after resource failures') })
      ]
    })
  });
}

ClientEvents.tickPost(() => {
  if (opened) return;
  opened = true;
  host = ClientUI.screen('Ticket 44 visual smoke', false);
  root = UI.createRoot(VisualScreen, host, { id: 'ticket44-visual-root' });
  host.bindRoot(root);
  host.open();
  console.info('[ticket44] visual screen opened');
});

export { host, root };
