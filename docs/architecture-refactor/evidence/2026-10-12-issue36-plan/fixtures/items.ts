/// <reference path="../../../../../src/test/resources/golden/registry/startup-builders.d.ts" />
declare const item: ItemBuilder;
declare const block: BlockBuilder;
item.components = { 'minecraft:rarity': 'epic' };
item.component('minecraft:custom_data', { value: [1, true] });
item.jukeboxPlayable = 'minecraft:cat';
item.trimMaterial = 'minecraft:diamond';
item.parentModel = 'minecraft:item/generated';
item.textures = { layer0: 'example:item/gem' };
item.texture('minecraft:item/diamond');
item.texture('layer1', 'example:item/overlay');
block.item(child => { child.texture('minecraft:item/emerald'); });
block.item.maxStackSize = 16;
// @ts-expect-error Texture maps accept resource identifier strings.
item.textures = { layer0: 42 };
// @ts-expect-error The single component overload requires its value.
item.component('minecraft:rarity');
// @ts-expect-error Nested callbacks receive the actual ItemBuilder interface.
block.item(child => { child.unlistedProperty = true; });
