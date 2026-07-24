package com.tkisor.nekojs.wrapper.registry;

import lombok.Getter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class BlockBuilderJS {
    @Getter
    private final Identifier location;

    private float hardness = 1.5f;
    private float resistance = 1.5f;
    private int lightLevel = 0;
    private boolean generateItem = true;
    private boolean requiresTool = false;
    private SoundType soundType = SoundType.STONE;

    public BlockBuilderJS(Identifier location) {
        this.location = location;
    }

    public BlockBuilderJS hardness(float hardness) { this.hardness = hardness; return this; }
    public BlockBuilderJS resistance(float resistance) { this.resistance = resistance; return this; }

    public BlockBuilderJS unbreakable() {
        this.hardness = -1.0f;
        this.resistance = 3600000.0f;
        return this;
    }

    public BlockBuilderJS lightLevel(int lightLevel) { this.lightLevel = lightLevel; return this; }

    public BlockBuilderJS requiresTool() { this.requiresTool = true; return this; }

    public BlockBuilderJS noItem() { this.generateItem = false; return this; }

    public BlockBuilderJS sound(String sound) {
        this.soundType = switch (sound.toLowerCase()) {
            case "wood" -> SoundType.WOOD;
            case "gravel" -> SoundType.GRAVEL;
            case "grass" -> SoundType.GRASS;
            case "metal" -> SoundType.METAL;
            case "glass" -> SoundType.GLASS;
            case "wool" -> SoundType.WOOL;
            case "sand" -> SoundType.SAND;
            case "snow" -> SoundType.SNOW;
            case "amethyst" -> SoundType.AMETHYST;
            default -> SoundType.STONE;
        };
        return this;
    }

    public boolean shouldGenerateItem() { return this.generateItem; }

    public Block createBlock() {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, location);

        BlockBehaviour.Properties props = BlockBehaviour.Properties.of()
                .setId(key)
                .destroyTime(hardness)
                .explosionResistance(resistance)
                .sound(soundType)
                .lightLevel(state -> lightLevel);

        if (requiresTool) {
            props.requiresCorrectToolForDrops();
        }

        return new Block(props);
    }
}