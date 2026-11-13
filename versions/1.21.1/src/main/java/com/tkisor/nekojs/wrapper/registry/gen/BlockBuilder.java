// 1.21.1 implementation, paired with the same-named 26.x file under src/ in the version tree.
// The content is that file evaluated at this node; keep this file in sync when 26.x behavior changes.
package com.tkisor.nekojs.wrapper.registry.gen;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Block builder and the template for the linked three-part registration:
 * <pre>
 * event.block('mymod:ruby_block', b =&gt; { b.hardness = 3; b.noItem() })
 * event.block('mymod:ruby_block', b =&gt; { b.item.maxStackSize = 16 })   // customize the automatic BlockItem
 * </pre>
 * Construction pre-creates the {@link #item} child builder, which registers a BlockItem by
 * default; {@link #noItem()} sets it to null to suppress that. This class implements
 * {@code Supplier}: the BlockItem gets the block through {@link #get()} lazily, so it is only
 * built when the registration event drains.
 */
public class BlockBuilder extends RegistryObjectBuilder<Block> {

    /**
     * Render type declared for the block. 26.x asset generation consumes it:
     * translucent uses the force_translucent texture reference.
     */

    private float hardness = 1.5f;
    private float resistance = 1.5f;
    private int lightLevel = 0;
    private boolean requiresTool = false;
    /** Sound type name: wood/gravel/grass/metal/glass/wool/sand/snow/amethyst (default stone). */
    private String sound = "stone";
    /** Map color name (such as 'dirt'/'water'/'gold'/'color_red'). Default stone. */
    private String mapColor = "stone";
    /**
     * Client render layer: solid / cutout / cutout_mipped / translucent. 26.x drives
     * this from the model, so it is documentation only.
     */
    private String renderType = null;

    /**
     * Pre-created BlockItem child builder: {@code b.item.maxStackSize = 16} customizes
     * it directly; {@link #noItem()} sets it to null.
     */
    private ItemBuilder item;
    private boolean itemDefinitionFrozen;

    public BlockBuilder(ResourceLocation id) {
        super(id);
        this.item = new ItemBuilder(id);
    }

    // ---- Data properties: the explicit setter and the JavaBean property share one write point ----

    public float getHardness() {
        return hardness;
    }

    public void setHardness(float hardness) {
        this.hardness = hardness;
    }

    public float getResistance() {
        return resistance;
    }

    public void setResistance(float resistance) {
        this.resistance = resistance;
    }

    public int getLightLevel() {
        return lightLevel;
    }

    public void setLightLevel(int lightLevel) {
        if (lightLevel < 0 || lightLevel > 15) {
            throw new IllegalArgumentException("lightLevel must be in [0, 15] but got " + lightLevel);
        }
        this.lightLevel = lightLevel;
    }

    public boolean isRequiresTool() {
        return requiresTool;
    }

    public void setRequiresTool(boolean requiresTool) {
        this.requiresTool = requiresTool;
    }

    /** Sound type name, normalized to lower case. */
    public String getSound() {
        return sound;
    }

    public void setSound(String sound) {
        this.sound = sound == null ? "stone" : sound.toLowerCase();
    }

    /** Map color name, normalized to lower case. */
    public String getMapColor() {
        return mapColor;
    }

    public void setMapColor(String mapColor) {
        this.mapColor = mapColor == null ? "stone" : mapColor.toLowerCase();
    }

    /** Render layer, normalized: blank counts as not declared, which build takes the same branch for anyway. */
    @com.tkisor.nekojs.api.annotation.ScriptNullable
    public String getRenderType() {
        return renderType;
    }

    public void setRenderType(@com.tkisor.nekojs.api.annotation.ScriptNullable String renderType) {
        this.renderType = renderType == null || renderType.isBlank() ? null : renderType;
    }

    /** Pre-created BlockItem child builder (wrapped by {@code BuilderSurface} on the script side). */
    @com.tkisor.nekojs.api.annotation.ScriptNullable
    public ItemBuilder getItem() {
        return item;
    }

    /**
     * The only write point for suppressing or restoring the linked BlockItem: both
     * {@code b.item = null} and {@link #noItem()} go through this setter. The script side only
     * accepts null, which suppresses the item; customization goes through {@code b.item.xxx} or
     * {@link #item(Consumer)}.
     */
    public void setItem(@com.tkisor.nekojs.api.annotation.ScriptNullable ItemBuilder item) {
        if (itemDefinitionFrozen) throw new IllegalStateException("[NEKO-4035] Block item definition is frozen after collection");
        if (item == this.item) {
            return;
        }
        if (item == null) {
            this.item = null;
            return;
        }
        throw new IllegalArgumentException(
                "item only accepts null on the script surface (suppress the BlockItem); configure it via "
                        + "b.item.xxx = ... or b.item(cb) instead");
    }

    /** Unbreakable (hardness -1, blast resistance 3600000). */
    public void unbreakable() {
        setHardness(-1.0f);
        setResistance(3600000.0f);
    }

    /** Suppresses the automatic BlockItem registration (same write point as {@code b.item = null}). */
    public void noItem() {
        setItem(null);
    }

    /**
     * Customizes the automatic BlockItem properties (convenience form; equivalent to
     * editing the {@link #getItem()} child builder directly).
     */
    public void item(Consumer<ItemBuilder> consumer) {
        if (this.item == null) {
            throw new IllegalStateException("noItem() 已抑制 BlockItem 生成，不能再配置 item(cb)");
        }
        consumer.accept(this.item);
    }

    @Override
    void freezeDefinition() {
        itemDefinitionFrozen = true;
        if (item != null) item.freezeDefinition();
    }

    @Override
    public Block build() {

        BlockBehaviour.Properties props = BlockBehaviour.Properties.of()
                .mapColor(resolveMapColor(mapColor))
                .destroyTime(hardness)
                .explosionResistance(resistance)
                .sound(resolveSound(sound))
                .lightLevel(state -> lightLevel);

        if (requiresTool) {
            props.requiresCorrectToolForDrops();
        }

        Block block = new Block(props);
        if (renderType != null && !renderType.isBlank()
                && net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
            com.tkisor.nekojs.client.ClientBlockRenderTypes.apply(block, renderType);
        }
        return block;
    }

    /**
     * Linked registration: registers the BlockItem when the pre-created child builder
     * is not null (the ITEM pass runs after BLOCK).
     */
    @Override
    public void handleAdditionalObjects(RegistryObjectBuilder.AdditionalObjectRegistry registry) {
        ItemBuilder itemBuilder = this.item;
        if (itemBuilder == null) {
            return;
        }
        registry.additional(Registries.ITEM, id,
                itemBuilder.blockItemSupplier(() -> new BlockItem(BlockBuilder.this.get(), itemBuilder.buildProperties())));
    }

    private static SoundType resolveSound(String name) {
        return switch (name == null ? "stone" : name.toLowerCase()) {
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
    }

    /** Maps a common MapColor name to its constant. Falls back to STONE when nothing matches, keeping the default. */
    private static net.minecraft.world.level.material.MapColor resolveMapColor(String name) {
        if (name == null) return net.minecraft.world.level.material.MapColor.STONE;
        return switch (name.toLowerCase()) {
            case "none" -> net.minecraft.world.level.material.MapColor.NONE;
            case "grass" -> net.minecraft.world.level.material.MapColor.GRASS;
            case "sand" -> net.minecraft.world.level.material.MapColor.SAND;
            case "wool" -> net.minecraft.world.level.material.MapColor.WOOL;
            case "fire" -> net.minecraft.world.level.material.MapColor.FIRE;
            case "ice" -> net.minecraft.world.level.material.MapColor.ICE;
            case "metal" -> net.minecraft.world.level.material.MapColor.METAL;
            case "plant" -> net.minecraft.world.level.material.MapColor.PLANT;
            case "snow" -> net.minecraft.world.level.material.MapColor.SNOW;
            case "clay" -> net.minecraft.world.level.material.MapColor.CLAY;
            case "dirt" -> net.minecraft.world.level.material.MapColor.DIRT;
            case "water" -> net.minecraft.world.level.material.MapColor.WATER;
            case "wood" -> net.minecraft.world.level.material.MapColor.WOOD;
            case "quartz" -> net.minecraft.world.level.material.MapColor.QUARTZ;
            case "gold" -> net.minecraft.world.level.material.MapColor.GOLD;
            case "diamond" -> net.minecraft.world.level.material.MapColor.DIAMOND;
            case "lapis" -> net.minecraft.world.level.material.MapColor.LAPIS;
            case "emerald" -> net.minecraft.world.level.material.MapColor.EMERALD;
            case "podzol" -> net.minecraft.world.level.material.MapColor.PODZOL;
            case "nether" -> net.minecraft.world.level.material.MapColor.NETHER;
            case "orange" -> net.minecraft.world.level.material.MapColor.COLOR_ORANGE;
            case "magenta" -> net.minecraft.world.level.material.MapColor.COLOR_MAGENTA;
            case "light_blue" -> net.minecraft.world.level.material.MapColor.COLOR_LIGHT_BLUE;
            case "yellow" -> net.minecraft.world.level.material.MapColor.COLOR_YELLOW;
            case "light_green", "lime" -> net.minecraft.world.level.material.MapColor.COLOR_LIGHT_GREEN;
            case "pink" -> net.minecraft.world.level.material.MapColor.COLOR_PINK;
            case "gray", "grey" -> net.minecraft.world.level.material.MapColor.COLOR_GRAY;
            case "light_gray", "light_grey" -> net.minecraft.world.level.material.MapColor.COLOR_LIGHT_GRAY;
            case "cyan" -> net.minecraft.world.level.material.MapColor.COLOR_CYAN;
            case "purple" -> net.minecraft.world.level.material.MapColor.COLOR_PURPLE;
            case "blue" -> net.minecraft.world.level.material.MapColor.COLOR_BLUE;
            case "brown" -> net.minecraft.world.level.material.MapColor.COLOR_BROWN;
            case "green" -> net.minecraft.world.level.material.MapColor.COLOR_GREEN;
            case "red" -> net.minecraft.world.level.material.MapColor.COLOR_RED;
            case "black" -> net.minecraft.world.level.material.MapColor.COLOR_BLACK;
            default -> net.minecraft.world.level.material.MapColor.STONE;
        };
    }
}
