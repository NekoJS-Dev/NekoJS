package com.tkisor.nekojs.wrapper.registry;

import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.function.Consumer;

public class BlockBuilderJS {
    @Getter
    private final ResourceLocation location;

    private float hardness = 1.5f;
    private float resistance = 1.5f;
    private int lightLevel = 0;
    private boolean generateItem = true;
    private boolean requiresTool = false;
    private SoundType soundType = SoundType.STONE;
    private net.minecraft.world.level.material.MapColor mapColor = net.minecraft.world.level.material.MapColor.STONE;

    /** 客户端渲染层：solid / cutout / cutout_mipped / translucent。由客户端 setup 应用。 */
    private String renderType = null;

    /** 可选：配置自动创建的 BlockItem（rarity/stackSize 等）。null 表示用默认 Item.Properties。 */
    private ItemBuilderJS itemBuilder = null;

    /** 实际注册时由 BlockRegistryEventJS 回填的 Block 实例（供 ITEM 分支创建 BlockItem 用）。 */
    private Block createdBlock = null;

    public BlockBuilderJS(ResourceLocation location) {
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

    /** 地图颜色（地名见 {@link #mapColor(String)}）。默认 STONE。 */
    public BlockBuilderJS mapColor(net.minecraft.world.level.material.MapColor mapColor) {
        if (mapColor != null) this.mapColor = mapColor;
        return this;
    }

    /** 按名称设地图颜色（如 {@code 'dirt'} / {@code 'water'} / {@code 'gold'} / {@code 'color_red'}）。默认 stone。 */
    public BlockBuilderJS mapColor(String name) {
        net.minecraft.world.level.material.MapColor resolved = resolveMapColor(name);
        if (resolved != null) this.mapColor = resolved;
        return this;
    }

    public BlockBuilderJS requiresTool() { this.requiresTool = true; return this; }

    /**
     * 客户端渲染层：{@code 'solid'} / {@code 'cutout'} / {@code 'cutout_mipped'} / {@code 'translucent'}
     * （做玻璃等透明方块）。1.21.1 会在客户端 setup 时经
     * {@code ItemBlockRenderTypes.setRenderLayer} 应用。
     */
    public BlockBuilderJS renderType(String renderType) {
        this.renderType = renderType == null || renderType.isBlank() ? null : renderType;
        return this;
    }

    /** 取客户端渲染层（可能为 null = 默认 solid）。 */
    public String getRenderType() { return renderType; }

    public BlockBuilderJS noItem() { this.generateItem = false; return this; }

    /** 配置自动创建的 BlockItem（不影响是否生成，仅定制属性；与 {@link #noItem()} 互斥）。 */
    public BlockBuilderJS item(Consumer<ItemBuilderJS> consumer) {
        ItemBuilderJS builder = new ItemBuilderJS(location);
        consumer.accept(builder);
        this.itemBuilder = builder;
        return this;
    }

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

    /** 取自动 BlockItem 的属性 builder（可能为 null = 用默认 Item.Properties）。 */
    public ItemBuilderJS getItemBuilder() { return itemBuilder; }

    /** 回填实际创建的 Block（由 BlockRegistryEventJS.registerAll 调用）。 */
    public void setCreatedBlock(Block block) { this.createdBlock = block; }

    /** 取实际创建的 Block（ITEM 分支创建 BlockItem 时用）。 */
    public Block getCreatedBlock() { return createdBlock; }

    public Block createBlock() {
        BlockBehaviour.Properties props = BlockBehaviour.Properties.of()
                .mapColor(mapColor)
                .destroyTime(hardness)
                .explosionResistance(resistance)
                .sound(soundType)
                .lightLevel(state -> lightLevel);

        if (requiresTool) {
            props.requiresCorrectToolForDrops();
        }

        return new Block(props);
    }

    /** 常用 MapColor 名称 → 常量。未命中返回 null（保留默认）。 */
    private static net.minecraft.world.level.material.MapColor resolveMapColor(String name) {
        if (name == null) return null;
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
            case "stone" -> net.minecraft.world.level.material.MapColor.STONE;
            case "water" -> net.minecraft.world.level.material.MapColor.WATER;
            case "wood" -> net.minecraft.world.level.material.MapColor.WOOD;
            case "quartz" -> net.minecraft.world.level.material.MapColor.QUARTZ;
            case "gold" -> net.minecraft.world.level.material.MapColor.GOLD;
            case "diamond" -> net.minecraft.world.level.material.MapColor.DIAMOND;
            case "lapis" -> net.minecraft.world.level.material.MapColor.LAPIS;
            case "emerald" -> net.minecraft.world.level.material.MapColor.EMERALD;
            case "podzol" -> net.minecraft.world.level.material.MapColor.PODZOL;
            case "nether" -> net.minecraft.world.level.material.MapColor.NETHER;
            case "color_orange", "orange" -> net.minecraft.world.level.material.MapColor.COLOR_ORANGE;
            case "color_magenta", "magenta" -> net.minecraft.world.level.material.MapColor.COLOR_MAGENTA;
            case "color_light_blue", "light_blue" -> net.minecraft.world.level.material.MapColor.COLOR_LIGHT_BLUE;
            case "color_yellow", "yellow" -> net.minecraft.world.level.material.MapColor.COLOR_YELLOW;
            case "color_light_green", "light_green", "lime" -> net.minecraft.world.level.material.MapColor.COLOR_LIGHT_GREEN;
            case "color_pink", "pink" -> net.minecraft.world.level.material.MapColor.COLOR_PINK;
            case "color_gray", "gray", "grey" -> net.minecraft.world.level.material.MapColor.COLOR_GRAY;
            case "color_light_gray", "light_gray", "light_grey" -> net.minecraft.world.level.material.MapColor.COLOR_LIGHT_GRAY;
            case "color_cyan", "cyan" -> net.minecraft.world.level.material.MapColor.COLOR_CYAN;
            case "color_purple", "purple" -> net.minecraft.world.level.material.MapColor.COLOR_PURPLE;
            case "color_blue", "blue" -> net.minecraft.world.level.material.MapColor.COLOR_BLUE;
            case "color_brown", "brown" -> net.minecraft.world.level.material.MapColor.COLOR_BROWN;
            case "color_green", "green" -> net.minecraft.world.level.material.MapColor.COLOR_GREEN;
            case "color_red", "red" -> net.minecraft.world.level.material.MapColor.COLOR_RED;
            case "color_black", "black" -> net.minecraft.world.level.material.MapColor.COLOR_BLACK;
            default -> null;
        };
    }
}