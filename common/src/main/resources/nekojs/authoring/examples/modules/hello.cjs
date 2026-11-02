// Copy this entry and lib/message.cjs together, preserving their relative paths.
const { formatGreeting } = require("./lib/message.cjs");
const message = formatGreeting("NekoJS");
console.info(message);
module.exports = { message };
