import { UI } from 'nekojs/jsx-runtime';

let opened = false;
let host;
let root;
let renders = 0;

function findNode(nodes, id) {
  for (const node of nodes) {
    if (node.id === id) return node;
    const nested = findNode(node.children, id);
    if (nested) return nested;
  }
  return null;
}

function measurements() {
  const snapshot = root.layout();
  const plain = findNode(snapshot.nodes, 'plain');
  const selected = findNode(snapshot.nodes, 'selected');
  console.info('[round4-font] measurement=' + JSON.stringify({
    profile: snapshot.profile, renders,
    plainWidth: plain.rect.width, selectedWidth: selected.rect.width,
    font: selected.style.font
  }));
}

function FontScreen() {
  renders++;
  return UI.element('screen', { id: 'round4-font-screen', closeOnEscape: true,
    children: UI.element('column', { id: 'font-content', width: 340, height: 216, padding: 8, spacing: 8,
      children: [
        UI.element('label', { id: 'heading', text: 'Controlled font selection and fallback', height: 14 }),
        UI.element('label', { id: 'plain', text: 'AAAA', height: 12 }),
        UI.element('label', { id: 'selected', text: 'AAAA', font: 'ticket44_round4:wide', height: 12 }),
        UI.element('label', { id: 'selected-large', text: 'AAAA', font: 'ticket44_round4:wide', fontSize: 18, height: 24 }),
        UI.element('label', { id: 'missing', text: 'Missing font uses readable default', font: 'ticket44_round4:missing', height: 12 }),
        UI.element('label', { id: 'corrupt', text: 'Corrupt definition uses readable default', font: 'ticket44_round4:broken', height: 12 }),
        UI.element('button', { id: 'measure', text: 'Measure / healthy callback', width: 230, height: 22,
          tooltip: 'Font geometry comes from the native adapter', onClick: measurements }),
        UI.element('label', { id: 'help', text: 'Compare default A with the wide graphic glyphs.', height: 12 })
      ]
    })
  });
}

ClientEvents.tickPost(() => {
  if (opened) return;
  opened = true;
  host = ClientUI.screen('Round 4 font acceptance', false);
  root = UI.createRoot(FontScreen, host, { id: 'round4-font-root' });
  host.bindRoot(root);
  host.open();
  measurements();
});

export { host, root };
