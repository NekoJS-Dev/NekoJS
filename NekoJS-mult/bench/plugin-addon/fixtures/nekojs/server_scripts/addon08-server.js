// Ticket 08 fixture: consume BOTH frozen addon products from SERVER scripts —
// the nekojs:bindings product (global `AddonProbe`) and the nekojs:node_modules product
// (require('nekojs08:hello')). Both come from the same external addon jar and the same
// process-level Plugin Runtime, so a normal script reload must not re-bootstrap them.
console.info('NEKOJS-ADDON08 server-binding ' + AddonProbe.hit('server'));
const addonHello = require('nekojs08:hello');
console.info('NEKOJS-ADDON08 server-module ' + addonHello.marker('server'));
// reload-survival witnesses: emit the addon-side instance count and the binding object identity
console.info('NEKOJS-ADDON08 server-instances ' + Java.type('com.example.nekojs08.AddonPlugin').instanceCount());
