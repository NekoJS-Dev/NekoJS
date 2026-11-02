// Requires the NeoForge tag-reload path; follow server script reload with /reload.
ServerEvents.tags("minecraft:item", event => {
    event.add("nekojs_example:stones", "minecraft:stone");
    event.add("nekojs_example:stones", "minecraft:granite");
    event.remove("nekojs_example:stones", "minecraft:granite");
});
