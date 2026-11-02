// STARTUP only: restart the whole process. Use a disposable world and matching client/server definitions.
RegistryEvents.register(event => {
    event.item("nekojs_example:ruby", build => {
        build.maxStackSize = 16;
        build.rarity = "rare";
    });
    event.block("nekojs_example:ruby_block", build => {
        build.hardness = 2;
        build.resistance = 6;
        build.lightLevel = 3;
    });
});
