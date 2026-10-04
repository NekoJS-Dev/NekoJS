import { UI } from 'nekojs/jsx-runtime';

const profileInputs = [
  { width: 100, height: 100, guiScale: 2, designWidth: 320, designHeight: 180 },
  { width: 320, height: 180, guiScale: 2, designWidth: 320, designHeight: 180 },
  { width: 480, height: 240, guiScale: 2, designWidth: 320, designHeight: 180 },
  { width: 640, height: 360, guiScale: 2, designWidth: 320, designHeight: 180 },
  { width: 854, height: 480, guiScale: 2, designWidth: 320, designHeight: 180 },
  { width: 1280, height: 720, guiScale: 2, designWidth: 320, designHeight: 180 }
];
let host, root;
let opened = false;
let sampled = false;
let selectedProfile = 0;
let renderCount = 0;
let hitCount = 0;

function field(value, name) {
  return typeof value[name] === 'function' ? value[name]() : value[name];
}

function listValues(value) {
  if (Array.isArray(value)) return value;
  const values = [];
  for (let index = 0; index < value.size(); index++) values.push(value.get(index));
  return values;
}

function rectData(rect) {
  return {
    x: field(rect, 'x'), y: field(rect, 'y'),
    width: field(rect, 'width'), height: field(rect, 'height')
  };
}

function nodeData(node, inspector) {
  const style = field(node, 'style');
  const width = inspector ? style.get('width') : style.width;
  const visible = field(node, 'visible');
  return {
    id: field(node, 'id'), type: field(node, 'type'), visible,
    rect: rectData(field(node, 'rect')), clip: rectData(field(node, 'clip')),
    resolvedWidth: width,
    children: listValues(field(node, 'children')).map(child => nodeData(child, inspector))
  };
}

function findNode(nodes, id) {
  for (const node of nodes) {
    if (node.id === id) return node;
    const child = findNode(node.children, id);
    if (child != null) return child;
  }
  return null;
}

function report(label) {
  const layout = root.layout();
  const inspected = host.inspect();
  const commonNodes = layout.nodes.map(node => nodeData(node, false));
  const inspectorNodes = listValues(field(inspected, 'nodes')).map(node => nodeData(node, true));
  const target = findNode(commonNodes, 'parity-target');
  const inspectorTarget = findNode(inspectorNodes, 'parity-target');
  console.info('[ticket43] measurement ' + JSON.stringify({
    label, profile: layout.profile, viewport: layout.viewport,
    inspectorProfile: field(inspected, 'profile'), renderCount, hitCount,
    targetCenter: {
      x: target.rect.x + target.rect.width / 2,
      y: target.rect.y + target.rect.height / 2
    },
    inspectorMatchesCommon: JSON.stringify(target.rect) === JSON.stringify(inspectorTarget.rect),
    commonNodes, inspectorNodes
  }));
}

function chooseProfile(index) {
  selectedProfile = index;
  const beforeRender = renderCount;
  const resized = root.resize(profileInputs[index]);
  console.info('[ticket43] resize ' + JSON.stringify({
    requestedProfile: index + 1, result: resized,
    measuredProfile: root.layout().profile,
    renderUnchanged: renderCount === beforeRender
  }));
  report('explicit-profile-' + (index + 1));
}

function nextProfile() {
  chooseProfile((selectedProfile + 1) % profileInputs.length);
}

function renderProfiles() {
  renderCount++;
  return UI.element('screen', {
    id: 'profiles-screen', closeOnEscape: true,
    children: UI.element('column', {
      id: 'profiles-panel', width: 280, height: 340, padding: 8, spacing: 8,
      children: [
        UI.element('label', { id: 'heading', text: 'Ticket 43 layout parity', height: 20 }),
        UI.element('row', {
          id: 'parity-row', width: 200, height: 28, justify: 'end',
          children: UI.element('button', {
            id: 'parity-target', text: 'HIT', width: 40, height: 20,
            onClick: event => {
              hitCount++;
              console.info('[ticket43] target-hit ' + JSON.stringify({
                count: hitCount, x: event.x, y: event.y,
                expected: findNode(root.layout().nodes, 'parity-target').rect
              }));
            }
          })
        }),
        UI.element('panel', {
          id: 'responsive-panel', height: 24,
          width: { base: 80, profiles: { 4: 120, 6: 160 } },
          background: '#446688'
        }),
        UI.element('panel', {
          id: 'percentage-panel', width: '50%', minWidth: 60, maxWidth: 100,
          height: 24, background: '#886644'
        }),
        UI.element('stack', {
          id: 'anchored-stack', width: 100, height: 40,
          children: UI.element('panel', {
            id: 'bottom-right', width: 20, height: 10, anchor: 'bottomRight',
            background: '#44AA66'
          })
        }),
        UI.element('row', {
          id: 'actions', width: 240, height: 28, spacing: 8,
          children: [
            UI.element('button', { id: 'next-profile', text: 'Next', width: 64, height: 20, onClick: nextProfile }),
            UI.element('button', { id: 'measure', text: 'Measure', width: 80, height: 20, onClick: () => report('manual') })
          ]
        })
      ]
    })
  });
}

ClientEvents.tickPost(() => {
  if (!opened) {
    opened = true;
    host = ClientUI.screen('Ticket 43 profile measurements', false);
    root = UI.createRoot(renderProfiles, host, { id: 'ticket43-profiles-root' });
    host.bindRoot(root);
    host.open();
    console.info('[ticket43] opened');
    return;
  }
  if (!sampled && !root.isDisposed()) {
    sampled = true;
    report('live-window-initial');
    for (let index = 0; index < profileInputs.length; index++) chooseProfile(index);
    const logicalWindow = host.viewport();
    root.resize(logicalWindow);
    report('live-window-restored');
    console.info('[ticket43] ready for target hit and real window resize');
  }
});

export { host, root, profileInputs };
