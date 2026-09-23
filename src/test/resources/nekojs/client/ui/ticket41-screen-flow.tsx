import { UI } from 'nekojs/jsx-runtime';

const host = ClientUI.screen('Ticket 41', false);
const root = UI.createRoot(() => UI.element('screen', {
  id: 'ticket41-screen',
  closeOnEscape: true,
  children: UI.element('button', { id: 'ok', text: 'OK', onClick: event => event.target })
}), host, { id: 'ticket41-root' });
host.bindRoot(root);
host.open();

export { host, root };
