import { StartupEvents } from "nekojs:event/versioned";

// STARTUP only: restart the whole process. Use a disposable world and matching client/server definitions.
StartupEvents.registry("item", event => {
    event.create("nekojs_example:ruby", build => {
        build.maxStackSize = 16;
        build.rarity = "rare";
    });
});

StartupEvents.registry("block", event => {
    event.create("nekojs_example:ruby_block", build => {
        build.hardness = 2;
        build.resistance = 6;
        build.lightLevel = 3;
    });
});
