// 26.x 实现，本文件不应再出现版本守卫。1.21.1 的实现是 versions/1.21.1/src 下的同名文件，
// 改本文件行为时须同步它。
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
 * 方块 builder（连带注册三件套的样板，ticket 15 单一写入语义）：
 * <pre>
 * event.block('mymod:ruby_block', b =&gt; { b.hardness = 3; b.noItem() })
 * event.block('mymod:ruby_block', b =&gt; { b.item.maxStackSize = 16 })   // 定制自动 BlockItem
 * </pre>
 * 三件套：构造期预创建 {@link #item} 子 builder（默认连带注册 BlockItem）、
 * {@link #noItem()} 抑制（与 {@code b.item = null} 走同一 {@link #setItem} 写入点）、
 * 本类 {@code implements Supplier}（BlockItem 经 {@link #get()} 懒引用方块，
 * 注册事件抽干期才构建）。数据属性私有 + setter，两种写法同一写入语义。
 */
public class BlockBuilder extends RegistryObjectBuilder<Block> implements TaggableBuilder<BlockBuilder> {

    /** 声明了 renderType 的方块（26.x 资产生成消费：translucent 用 force_translucent 贴图引用）。 */
    public static final Map<Identifier, String> RENDER_TYPES = new HashMap<>();

    private float hardness = 1.5f;
    private float resistance = 1.5f;
    private int lightLevel = 0;
    private boolean requiresTool = false;
    /** 声音类型名：wood/gravel/grass/metal/glass/wool/sand/snow/amethyst（默认 stone）。 */
    private String sound = "stone";
    /** 地图颜色名（如 'dirt'/'water'/'gold'/'color_red'）。默认 stone。 */
    private String mapColor = "stone";
    /** 客户端渲染层：solid / cutout / cutout_mipped / translucent。26.x 模型驱动，仅文档意义。 */
    private String renderType = null;

    /** 预创建的 BlockItem 子 builder：{@code b.item.maxStackSize = 16} 直接定制；{@link #noItem()} 抑制。 */
    private ItemBuilder item;

    public BlockBuilder(Identifier id) {
        super(id);
        this.item = new ItemBuilder(id);
    }

    /** {@link TaggableBuilder}：方块 tag 归属 BLOCK 注册表（给 BlockItem 打 tag 走 {@code item}）。 */
    @Override
    public net.minecraft.resources.ResourceKey<? extends net.minecraft.core.Registry<?>> getTagRegistry() {
        return Registries.BLOCK;
    }

    @Override
    public Identifier getLocation() {
        return id;
    }

    // ---- 数据属性：显式 setter 与 JavaBean property 同一写入点 ----

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

    /** 声音类型名（已归一化小写）。 */
    public String getSound() {
        return sound;
    }

    public void setSound(String sound) {
        this.sound = sound == null ? "stone" : sound.toLowerCase();
    }

    /** 地图颜色名（已归一化小写）。 */
    public String getMapColor() {
        return mapColor;
    }

    public void setMapColor(String mapColor) {
        this.mapColor = mapColor == null ? "stone" : mapColor.toLowerCase();
    }

    /** 渲染层（已归一化：空白视为未声明——build 期本来就走同一分支）。 */
    public String getRenderType() {
        return renderType;
    }

    public void setRenderType(String renderType) {
        this.renderType = renderType == null || renderType.isBlank() ? null : renderType;
    }

    /** 预创建的 BlockItem 子 builder（脚本面经 {@link BuilderSurface} 包装）。 */
    public ItemBuilder getItem() {
        return item;
    }

    /**
     * 连带 BlockItem 抑制/恢复的唯一写入点：{@code b.item = null} 与 {@link #noItem()}
     * 走本 setter。脚本侧只接受 null（抑制）；恢复默认子 builder 用 Java 面
     * （脚本的定制走 {@code b.item.xxx} 或 {@link #item(Consumer)}）。
     */
    public void setItem(ItemBuilder item) {
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

    // ---- 复合配置 ----

    /** 不可破坏（硬度 -1 / 抗爆 3600000）。 */
    public void unbreakable() {
        setHardness(-1.0f);
        setResistance(3600000.0f);
    }

    /** 抑制自动 BlockItem 连带注册（与 {@code b.item = null} 同一写入点）。 */
    public void noItem() {
        setItem(null);
    }

    /** 定制自动 BlockItem 属性（便捷面；等价于直接改 {@link #getItem()} 子 builder）。 */
    public void item(Consumer<ItemBuilder> consumer) {
        if (this.item == null) {
            throw new IllegalStateException("noItem() 已抑制 BlockItem 生成，不能再配置 item(cb)");
        }
        consumer.accept(this.item);
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

    /** 连带注册：预创建子 builder 非 null 时注册 BlockItem（ITEM pass 在 BLOCK 之后）。 */
    @Override
    public void handleAdditionalObjects(RegistryObjectBuilder.AdditionalObjectRegistry registry) {
        ItemBuilder itemBuilder = this.item;
        if (itemBuilder == null) {
            return;
        }
        registry.additional(Registries.ITEM, id,
                () -> new BlockItem(BlockBuilder.this.get(), itemBuilder.buildProperties()));
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

    /** 常用 MapColor 名称 → 常量。未命中回退 STONE（保留默认）。 */
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
