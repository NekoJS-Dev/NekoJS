package com.tkisor.nekojs.wrapper.registry;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.util.ResourceLocation;

/**
 * 1.12.2 BlockBuilderJS — builds Block instances from script definitions.
 * Adapted from neoforge-26.1 BlockBuilderJS.
 */
@Doc("Builder for registering a new block; obtain it from RegistryEvents.block.create(id).")
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

    /** Creates a builder for the given registry name. */
    public BlockBuilderJS(String registryName) {
        this.registryName = registryName;
    }

    /** Sets the block material from a name string. */
    @Doc("Sets the block material from a name.")
    @Param(name = "materialName", value = "material name like 'rock', 'wood', 'iron', 'water', 'leaves'; unknown names fall back to rock")
    @Return("this builder, for chaining")
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

    /** Sets the block hardness. */
    @Doc("Sets the block hardness (mining time scale).")
    @Param(name = "hardness", value = "hardness value; -1 makes the block unbreakable together with unbreakable()")
    @Return("this builder, for chaining")
    public BlockBuilderJS hardness(float hardness) {
        this.hardness = hardness;
        return this;
    }

    /** Sets the blast resistance. */
    @Doc("Sets the block's blast resistance.")
    @Param(name = "resistance", value = "resistance value; higher resists explosions more")
    @Return("this builder, for chaining")
    public BlockBuilderJS resistance(float resistance) {
        this.resistance = resistance;
        return this;
    }

    /** Makes the block unbreakable. */
    @Doc("Makes the block unbreakable (hardness -1, max resistance).")
    @Return("this builder, for chaining")
    public BlockBuilderJS unbreakable() {
        this.hardness = -1.0f;
        this.resistance = 3600000.0f;
        return this;
    }

    /** Sets the emitted light level. */
    @Doc("Sets the block's emitted light level.")
    @Param(name = "lightLevel", value = "light level from 0 to 15; clamped")
    @Return("this builder, for chaining")
    public BlockBuilderJS lightLevel(int lightLevel) {
        this.lightLevel = Math.max(0, Math.min(15, lightLevel));
        return this;
    }

    /** Sets the light opacity. */
    @Doc("Sets how much the block blocks light.")
    @Param(name = "lightOpacity", value = "opacity from 0.0 (transparent) to 1.0 (opaque); clamped")
    @Return("this builder, for chaining")
    public BlockBuilderJS lightOpacity(float lightOpacity) {
        this.lightOpacity = Math.max(0.0f, Math.min(1.0f, lightOpacity));
        return this;
    }

    /** Marks the block as requiring the correct tool to drop. */
    @Doc("Requires the correct harvest tool for the block to drop.")
    @Return("this builder, for chaining")
    public BlockBuilderJS requiresTool() {
        this.requiresTool = true;
        return this;
    }

    /** Suppresses item form generation. */
    @Doc("Suppresses generation of the block's item form.")
    @Return("this builder, for chaining")
    public BlockBuilderJS noItem() {
        this.generateItem = false;
        return this;
    }

    /**
     * 1.12.2 的 {@code Block.setSoundType} 是 protected（只能在子类里调用），所以 cleanroom 平台
     * 暂不应用声音——方法保留以保证脚本 API 兼容（调用成功但无效）。neoforge 平台会真正设置声音。
     */
    @Doc("Sets the block's sound type — accepted but not applied on 1.12.2 (setSoundType is protected).")
    @Param(name = "sound", value = "sound type name; ignored on this platform")
    @Return("this builder, for chaining")
    public BlockBuilderJS sound(String sound) {
        return this;
    }

    /** Sets the harvest tool and level. */
    @Doc("Sets the tool and tool level required to harvest the block.")
    @Param(name = "tool", value = "tool class like 'pickaxe', 'axe', 'shovel'")
    @Param(name = "level", value = "harvest level, e.g. 0 wood, 1 stone, 2 iron, 3 diamond")
    @Return("this builder, for chaining")
    public BlockBuilderJS harvestTool(String tool, int level) {
        this.harvestTool = tool;
        this.harvestLevel = level;
        return this;
    }

    /** Whether an item form of this block should be generated. */
    @Doc("Whether an item form of this block will be generated.")
    @Return("true unless noItem() was called")
    public boolean shouldGenerateItem() {
        return this.generateItem;
    }

    /** The registry name given at creation. */
    @Doc("Gets the registry name of the block being built.")
    @Return("the registry name string")
    public String getRegistryName() {
        return registryName;
    }

    /**
     * Build and create the block. Does NOT register it.
     * Registration should be done via RegistryEvent.Register&lt;Block&gt;.
     */
    @Doc("Builds the block instance; does not register it.")
    @Doc("Registration happens automatically when the registry event completes.")
    @Return("the configured block")
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
