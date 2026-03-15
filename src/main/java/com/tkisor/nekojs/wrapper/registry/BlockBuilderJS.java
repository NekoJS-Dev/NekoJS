package com.tkisor.nekojs.wrapper.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class BlockBuilderJS {
    private final String id;
    private float hardness = 1.5f;
    private float resistance = 1.5f;
    private int lightLevel = 0;

    private boolean generateItem = true;

    public BlockBuilderJS(String id) {
        this.id = id;
    }

    public BlockBuilderJS hardness(float hardness) { this.hardness = hardness; return this; }
    public BlockBuilderJS resistance(float resistance) { this.resistance = resistance; return this; }
    public BlockBuilderJS lightLevel(int lightLevel) { this.lightLevel = lightLevel; return this; }

    public BlockBuilderJS noItem() {
        this.generateItem = false;
        return this;
    }

    public boolean shouldGenerateItem() { return this.generateItem; }

    public Block createBlock(Identifier location) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, location);
        BlockBehaviour.Properties props = BlockBehaviour.Properties.of()
                .setId(key)
                .destroyTime(hardness)
                .explosionResistance(resistance)
                .lightLevel(state -> lightLevel);
        return new Block(props);
    }
}