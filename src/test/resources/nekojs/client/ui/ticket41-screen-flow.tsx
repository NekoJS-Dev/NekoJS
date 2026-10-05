import { UI } from 'nekojs/jsx-runtime';

let host, root;
let opened = false;
ClientEvents.tickPost(() => {
  if (opened) return;
  opened = true;
  host = ClientUI.screen('Ticket 41', false);
  root = UI.createRoot(() => UI.element('screen', {
    id: 'ticket41-screen',
    closeOnEscape: true,
    children: UI.element('button', { id: 'ok', text: 'OK', onClick: event => console.info('[ticket41] clicked ' + event.target) })
  }), host, { id: 'ticket41-root' });
  host.bindRoot(root);
  host.open();
  console.info('[ticket41] screen opened');
});

export { host, root };
