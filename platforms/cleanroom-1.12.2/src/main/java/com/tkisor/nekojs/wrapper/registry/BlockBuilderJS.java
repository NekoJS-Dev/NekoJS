package com.tkisor.nekojs.wrapper.registry;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.util.ResourceLocation;

/**
 * 1.12.2 BlockBuilderJS — builds Block instances from script definitions.
 * Adapted from neoforge-26.1 BlockBuilderJS.
 */
public class BlockBuilderJS {
    private final String registryName;
    private Material material = Material.ROCK;
    private float hardness = 1.5f;
    private float resistance = 1.5f;
    private int lightLevel = 0;
    private float lightOpacity = 1.0f;
    private boolean generateItem = true;
    private boolean requiresTool = false;
    private String harvestTool = null;
    private int harvestLevel = 0;

    public BlockBuilderJS(String registryName) {
        this.registryName = registryName;
    }

    public BlockBuilderJS material(String materialName) {
        this.material = switch (materialName.toLowerCase()) {
            case "rock", "stone" -> Material.ROCK;
            case "wood" -> Material.WOOD;
            case "ground", "dirt" -> Material.GROUND;
            case "iron" -> Material.IRON;
            case "anvil" -> Material.ANVIL;
            case "water" -> Material.WATER;
            case "lava" -> Material.LAVA;
            case "leaves" -> Material.LEAVES;
            case "plants", "plant" -> Material.PLANTS;
            case "vine" -> Material.VINE;
            case "sponge" -> Material.SPONGE;
            case "cloth" -> Material.CLOTH;
            case "fire" -> Material.FIRE;
            case "sand" -> Material.SAND;
            case "circuits", "redstone" -> Material.CIRCUITS;
            case "carpet" -> Material.CARPET;
            case "glass" -> Material.GLASS;
            case "ice", "packed_ice" -> Material.ICE;
            case "tnt" -> Material.TNT;
            case "crafted_snow", "snow" -> Material.CRAFTED_SNOW;
            case "cactus" -> Material.CACTUS;
            case "clay" -> Material.CLAY;
            case "gourd" -> Material.GOURD;
            case "dragon_egg" -> Material.DRAGON_EGG;
            case "portal" -> Material.PORTAL;
            case "cake" -> Material.CAKE;
            case "web" -> Material.WEB;
            default -> Material.ROCK;
        };
        return this;
    }

    public BlockBuilderJS hardness(float hardness) {
        this.hardness = hardness;
        return this;
    }

    public BlockBuilderJS resistance(float resistance) {
        this.resistance = resistance;
        return this;
    }

    public BlockBuilderJS unbreakable() {
        this.hardness = -1.0f;
        this.resistance = 3600000.0f;
        return this;
    }

    public BlockBuilderJS lightLevel(int lightLevel) {
        this.lightLevel = Math.max(0, Math.min(15, lightLevel));
        return this;
    }

    public BlockBuilderJS lightOpacity(float lightOpacity) {
        this.lightOpacity = Math.max(0.0f, Math.min(1.0f, lightOpacity));
        return this;
    }

    public BlockBuilderJS requiresTool() {
        this.requiresTool = true;
        return this;
    }

    public BlockBuilderJS noItem() {
        this.generateItem = false;
        return this;
    }

    /**
     * 1.12.2 的 {@code Block.setSoundType} 是 protected（只能在子类里调用），所以 cleanroom 平台
     * 暂不应用声音——方法保留以保证脚本 API 兼容（调用成功但无效）。neoforge 平台会真正设置声音。
     */
    public BlockBuilderJS sound(String sound) {
        return this;
    }

    public BlockBuilderJS harvestTool(String tool, int level) {
        this.harvestTool = tool;
        this.harvestLevel = level;
        return this;
    }

    /** Whether an item form of this block should be generated. */
    public boolean shouldGenerateItem() {
        return this.generateItem;
    }

    public String getRegistryName() {
        return registryName;
    }

    /**
     * Build and create the block. Does NOT register it.
     * Registration should be done via RegistryEvent.Register&lt;Block&gt;.
     */
    @SuppressWarnings("deprecation")
    public Block build() {
        Block block = new Block(material);
        ResourceLocation rl = new ResourceLocation(registryName);
        block.setRegistryName(rl);
        block.setTranslationKey(rl.getNamespace() + "." + rl.getPath());
        block.setHardness(hardness);
        block.setResistance(resistance);
        // setSoundType is protected in 1.12.2 Block
        block.setLightLevel(lightLevel / 15.0f);
        // setLightOpacity takes int in 1.12.2
        block.setLightOpacity((int)(lightOpacity * 255));
        if (harvestTool != null) {
            block.setHarvestLevel(harvestTool, harvestLevel);
        }
        return block;
    }
}
