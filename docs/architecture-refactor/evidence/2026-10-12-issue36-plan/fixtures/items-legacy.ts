/// <reference path="../../../../../src/test/resources/golden/registry/startup-builders-1.21.1.d.ts" />
declare const item: ItemBuilder;
declare const block: BlockBuilder;
item.components = { 'minecraft:rarity': 'rare' };
item.jukeboxPlayable = 'minecraft:cat';
item.textures = { layer0: 'example:item/gem' };
block.item(child => { child.component('minecraft:rarity', 'rare'); });
// @ts-expect-error The 1.21.1 node has no trimMaterial capability.
item.trimMaterial = 'minecraft:diamond';
// @ts-expect-error Texture maps accept resource identifier strings.
item.textures = { layer0: false };
