package com.tkisor.nekojs.wrapper.event.registry;

import com.tkisor.nekojs.wrapper.registry.FluidBuilderJS;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 流体注册事件对象（{@code StartupEvents.registry('fluid')}）。
 *
 * <p>单一脚本 {@code create(id)} 会跨 4 个 {@link RegisterEvent} pass 协调注册（依赖
 * GameData 的固定注册顺序 FLUID → BLOCK → ITEM，FLUID_TYPES 在 modded 字母尾）：
 * <ol>
 *   <li>FLUID_TYPES：注册 {@link FluidType}（{@link #registerTypes}）</li>
 *   <li>FLUID：注册 source（{@code id}）+ flowing（{@code flowing_<id>}），注入懒解析 Supplier
 *       （{@link #registerFluids}）。<b>不清 PENDING</b>，留给 BLOCK/ITEM 用。</li>
 *   <li>BLOCK：注册 {@link LiquidBlock}（{@link #registerBlocks}）</li>
 *   <li>ITEM：注册 {@link net.minecraft.world.item.BucketItem}（{@link #registerItems}），
 *       最后 {@code PENDING.clear()}</li>
 * </ol>
 *
 * <p>注意：FLUID 分支会 post 本事件两次（FLUID_TYPES 与 FLUID 都触发 RegistryEvents.FLUID
 * 监听），{@code create} 按 id 覆盖去重、幂等。两个 FLUID 分支都会执行 registerFluids，
 * 但 {@code event.register} 对同 id 第二次会覆盖，且 builder 的 FluidType 已缓存故无副作用。
 */
public class FluidRegistryEventJS {

    /** 跨 pass 持久化的待注册 builder；ITEM 分支末尾清理。 */
    private static final Map<ResourceLocation, FluidBuilderJS> PENDING = new HashMap<>();

    /** 已注册的 source/flowing/block/bucket 实例（FLUID/BLOCK/ITEM 分支填充，Supplier 解析时读）。 */
    private static final Map<ResourceLocation, Fluid> REGISTERED_SOURCES = new HashMap<>();
    private static final Map<ResourceLocation, Fluid> REGISTERED_FLOWING = new HashMap<>();
    private static final Map<ResourceLocation, LiquidBlock> REGISTERED_BLOCKS = new HashMap<>();
    private static final Map<ResourceLocation, Item> REGISTERED_BUCKETS = new HashMap<>();

    public FluidBuilderJS create(ResourceLocation id) {
        FluidBuilderJS builder = new FluidBuilderJS(id);
        PENDING.put(id, builder);
        return builder;
    }

    /** 便利重载：直接配置并注册（对齐 BlockRegistryEventJS）。 */
    public void create(ResourceLocation id, Consumer<FluidBuilderJS> consumer) {
        FluidBuilderJS builder = create(id);
        consumer.accept(builder);
    }

    /** 注册流体类型（FLUID_TYPES 分支）。 */
    public static void registerTypes(RegisterEvent event) {
        for (FluidBuilderJS builder : PENDING.values()) {
            event.register(NeoForgeRegistries.FLUID_TYPES.key(), builder.getLocation(), builder::createType);
        }
    }

    /**
     * 注册 source + flowing 流体（FLUID 分支）。注入懒解析 Supplier，注册完成后再可解析；
     * <b>不清 PENDING</b>（BLOCK/ITEM 分支还要用）。
     */
    public static void registerFluids(RegisterEvent event) {
        for (FluidBuilderJS builder : PENDING.values()) {
            ResourceLocation id = builder.getLocation();
            ResourceLocation flowingId = builder.getFlowingLocation();

            // 懒解析 Supplier：BLOCK/ITEM 分支注册完对应对象后填充 REGISTERED_*，Supplier 读它。
            // FLUID→BLOCK→ITEM 的固定顺序保证 BLOCK 构造 LiquidBlock 时 source 已就绪。
            Supplier<Fluid> sourceSupplier = () -> REGISTERED_SOURCES.get(id);
            Supplier<Fluid> flowingSupplier = () -> REGISTERED_FLOWING.get(id);
            Supplier<Item> bucketSupplier = builder.shouldGenerateBucket()
                    ? () -> REGISTERED_BUCKETS.get(id) : () -> null;
            Supplier<LiquidBlock> blockSupplier = builder.shouldGenerateBlock()
                    ? () -> REGISTERED_BLOCKS.get(id) : () -> null;
            builder.wireSuppliers(sourceSupplier, flowingSupplier, bucketSupplier, blockSupplier);

            // 注册 source（捕获到 REGISTERED_SOURCES，供 BLOCK 分支 LiquidBlock 引用）
            event.register(Registries.FLUID, id, () -> {
                Fluid source = builder.createSourceFluid();
                REGISTERED_SOURCES.put(id, source);
                return source;
            });
            // 注册 flowing（捕获到 REGISTERED_FLOWING）
            event.register(Registries.FLUID, flowingId, () -> {
                Fluid flowing = builder.createFlowingFluid();
                REGISTERED_FLOWING.put(id, flowing);
                return flowing;
            });
        }
    }

    /** 注册液体方块（BLOCK 分支）。 */
    public static void registerBlocks(RegisterEvent event) {
        for (FluidBuilderJS builder : PENDING.values()) {
            if (!builder.shouldGenerateBlock()) continue;
            ResourceLocation id = builder.getLocation();
            event.register(Registries.BLOCK, id, () -> {
                Fluid source = REGISTERED_SOURCES.get(id);
                LiquidBlock block = builder.createLiquidBlock(source);
                REGISTERED_BLOCKS.put(id, block);
                return block;
            });
        }
    }

    /** 注册桶物品（ITEM 分支），并清理所有跨 pass 状态。 */
    public static void registerItems(RegisterEvent event) {
        for (FluidBuilderJS builder : PENDING.values()) {
            if (!builder.shouldGenerateBucket()) continue;
            ResourceLocation id = builder.getLocation();
            ResourceLocation bucketId = builder.getBucketLocation();
            event.register(Registries.ITEM, bucketId, () -> {
                Fluid source = REGISTERED_SOURCES.get(id);
                Item bucket = builder.createBucketItem(source);
                REGISTERED_BUCKETS.put(id, bucket);
                return bucket;
            });
        }
        // 全部 pass 完成，清理跨 pass 状态
        PENDING.clear();
        REGISTERED_SOURCES.clear();
        REGISTERED_FLOWING.clear();
        REGISTERED_BLOCKS.clear();
        REGISTERED_BUCKETS.clear();
    }
}
