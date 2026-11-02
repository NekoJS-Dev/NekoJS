// Copy startup/shared-config.js first, then restart once. This counter is not saved to disk.
global.authoringLoads = (global.authoringLoads || 0) + 1;
console.info(`Authoring SERVER load #${global.authoringLoads}: ${shared.authoringMessage}`);
setTimeout(() => {
    console.info("Authoring timeout fired");
}, 1000);
