// 26.x implementation, this file should no longer contain version guards. The 1.21.1 implementation
// is the same-named file under versions/1.21.1/src; synchronize it when changing behavior here.
package com.tkisor.nekojs.wrapper.registry.gen;

import com.tkisor.nekojs.wrapper.registry.TaggableBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Block builder (boilerplate for the three-part registration):
 * <pre>
 * event.block('mymod:ruby_block', b =&gt; { b.hardness = 3; b.noItem() })
 * event.block('mymod:ruby_block', b =&gt; { b.item.maxStackSize = 16 })   // Customizes automatic BlockItem
 * </pre>
 * Three parts: the {@link #item} sub-builder is pre-created during construction (by default the
 * BlockItem registration is bundled), it is suppressed via {@link #noItem()} (the same
 * {@link #setItem} write point as {@code b.item = null}), and this class {@code implements Supplier}
 * (BlockItem references the block lazily via {@link #get()} and is constructed only during the
 * registry event drain phase). Data attributes are private with setters; both writing styles share
 * the same write semantics.
 */
public class BlockBuilder extends RegistryObjectBuilder<Block> implements TaggableBuilder<BlockBuilder> {

    /** Blocks with a declared renderType (26.x asset generation consumption: translucent uses force_translucent texture reference). */
    public static final Map<Identifier, String> RENDER_TYPES = new HashMap<>();

    private float hardness = 1.5f;
    private float resistance = 1.5f;
    private int lightLevel = 0;
    private boolean requiresTool = false;
    /** Sound type name: wood/gravel/grass/metal/glass/wool/sand/snow/amethyst (defaults to stone). */
    private String sound = "stone";
    /** Map color name (e.g., 'dirt'/'water'/'gold'/'color_red'). Defaults to stone. */
    private String mapColor = "stone";
    /** Client render layer: solid / cutout / cutout_mipped / translucent. 26.x model-driven, documentary only. */
    private String renderType = null;

    /** Pre-created BlockItem sub-builder: {@code b.item.maxStackSize = 16} customizes directly; {@link #noItem()} suppresses. */
    private ItemBuilder item;
    private boolean itemDefinitionFrozen;

    public BlockBuilder(Identifier id) {
        super(id);
        this.item = new ItemBuilder(id);
    }

    /** {@link TaggableBuilder}: Block tags belong to the BLOCK registry (tag BlockItem via {@code item}). */
    @Override
    public net.minecraft.resources.ResourceKey<? extends net.minecraft.core.Registry<?>> getTagRegistry() {
        return Registries.BLOCK;
    }

    @Override
    public Identifier getLocation() {
        return id;
    }

    // ---- Data attributes: explicit setters and JavaBean properties share one write point ----

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

    /** Sound type name (normalized to lowercase). */
    public String getSound() {
        return sound;
    }

    public void setSound(String sound) {
        this.sound = sound == null ? "stone" : sound.toLowerCase();
    }

    /** Map color name (normalized to lower case). */
    public String getMapColor() {
        return mapColor;
    }

    public void setMapColor(String mapColor) {
        this.mapColor = mapColor == null ? "stone" : mapColor.toLowerCase();
    }

    /** Render layer (normalized: blank counts as undeclared, because build takes the same branch anyway). */
    @com.tkisor.nekojs.api.annotation.ScriptNullable
    public String getRenderType() {
        return renderType;
    }

    public void setRenderType(@com.tkisor.nekojs.api.annotation.ScriptNullable String renderType) {
        this.renderType = renderType == null || renderType.isBlank() ? null : renderType;
    }

    /** Pre-created BlockItem sub-builder (the script surface wraps it in {@link BuilderSurface}). */
    @com.tkisor.nekojs.api.annotation.ScriptNullable
    public ItemBuilder getItem() {
        return item;
    }

    /**
     * The only write point for suppressing or restoring the bundled BlockItem: both
     * {@code b.item = null} and {@link #noItem()} go through this setter. The script side only
     * accepts null (suppress); restoring the default sub-builder is done from Java, and script
     * customization goes through {@code b.item.xxx} or {@link #item(Consumer)}.
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

    // ---- Composite configuration ----

    /** Makes the block unbreakable (hardness -1, blast resistance 3600000). */
    public void unbreakable() {
        setHardness(-1.0f);
        setResistance(3600000.0f);
    }

    /** Suppresses the automatic bundled BlockItem registration (same write point as {@code b.item = null}). */
    public void noItem() {
        setItem(null);
    }

    /** Configures the automatic BlockItem; same as editing the {@link #getItem()} sub-builder. */
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
        net.minecraft.resources.ResourceKey<Block> key =
                net.minecraft.resources.ResourceKey.create(Registries.BLOCK, id);
        if (renderType != null) {
            RENDER_TYPES.put(id, renderType);
        }

        BlockBehaviour.Properties props = BlockBehaviour.Properties.of()
                .setId(key)
                .mapColor(resolveMapColor(mapColor))
                .destroyTime(hardness)
                .explosionResistance(resistance)
                .sound(resolveSound(sound))
                .lightLevel(state -> lightLevel);

        if (requiresTool) {
            props.requiresCorrectToolForDrops();
        }

        Block block = new Block(props);
        return block;
    }

    /**
     * Bundled registration: registers the BlockItem when the pre-created sub-builder is not null
     * (the ITEM pass runs after BLOCK).
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

    /** Maps common MapColor names to constants. Falls back to STONE on a miss (keeps the default). */
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
