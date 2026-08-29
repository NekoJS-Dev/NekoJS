// 1.21.1 节点专有变体（DEVEX-ROADMAP 档 1 整文件拆分）：主干已 26.x 基准化，本文件为 1.21.1 的
// 完整实现（构造性变换）；主干行为变更时须同步本文件。
// TODO(loader-port): deferred to the LoaderBridge fabric port
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
 * 方块 builder（ADR-0005 连带注册三件套的样板）：
 * <pre>
 * event.block('mymod:ruby_block', b =&gt; { b.hardness = 3; b.noItem() })
 * event.block('mymod:ruby_block', b =&gt; { b.item.maxStackSize = 16 })   // 定制自动 BlockItem
 * </pre>
 * 三件套：构造期预创建 {@link #item} 子 builder（默认连带注册 BlockItem）、
 * {@link #noItem()} 置 null 抑制、本类 {@code implements Supplier}（BlockItem 经
 * {@link #get()} 懒引用方块，注册事件抽干期才构建）。
 */
public class BlockBuilder extends RegistryObjectBuilder<Block> {

    /** 声明了 renderType 的方块（26.x 资产生成消费：translucent 用 force_translucent 贴图引用）。 */

    public float hardness = 1.5f;
    public float resistance = 1.5f;
    public int lightLevel = 0;
    public boolean requiresTool = false;
    /** 声音类型名：wood/gravel/grass/metal/glass/wool/sand/snow/amethyst（默认 stone）。 */
    public String sound = "stone";
    /** 地图颜色名（如 'dirt'/'water'/'gold'/'color_red'）。默认 stone。 */
    public String mapColor = "stone";
    /** 客户端渲染层：solid / cutout / cutout_mipped / translucent。26.x 模型驱动，仅文档意义。 */
    public String renderType = null;

    /** 预创建的 BlockItem 子 builder：{@code b.item.maxStackSize = 16} 直接定制；{@link #noItem()} 置 null。 */
    public ItemBuilder item;

    public BlockBuilder(ResourceLocation id) {
        super(id);
        this.item = new ItemBuilder(id);
    }

    /** 不可破坏（硬度 -1 / 抗爆 3600000）。 */
    public void unbreakable() {
        this.hardness = -1.0f;
        this.resistance = 3600000.0f;
    }

    /** 抑制自动 BlockItem 连带注册（置 null 子 builder，与 {@code b.item = null} 等价）。 */
    public void noItem() {
        this.item = null;
    }

    /** 定制自动 BlockItem 属性（便捷面；等价于直接改 {@link #item} 字段）。 */
    public void item(Consumer<ItemBuilder> consumer) {
        if (this.item == null) {
            throw new IllegalStateException("noItem() 已抑制 BlockItem 生成，不能再配置 item(cb)");
        }
        consumer.accept(this.item);
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
