package com.tkisor.nekojs.wrapper.event.registry;

import com.tkisor.nekojs.wrapper.registry.FluidBuilderJS;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBucket;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fluids.BlockFluidClassic;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.registries.IForgeRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 1.12.2 流体注册事件对象（{@code StartupEvents.registry('fluid')}）。
 *
 * <p>1.12.2 没有 {@code RegistryEvent.Register<Fluid>}（FluidRegistry 是静态注册表），
 * 且注册顺序为 {@code Register<Block>} → {@code Register<Item>}。因此：
 * <ol>
 *   <li>BLOCK 分支开头：{@link #registerAll} 先 {@code FluidRegistry.registerFluid}，
 *       再注册 {@link BlockFluidClassic} 液体方块（构造器自动 {@code fluid.setBlock(this)}），
 *       并把需要桶的流体暂存进 {@link #PENDING_BUCKET_FLUIDS}</li>
 *   <li>ITEM 分支末尾：{@link #registerBucketItems} 注册 {@link ItemBucket} 并
 *       {@code FluidRegistry.addBucketForFluid}（纯记账），最后清理状态</li>
 * </ol>
 */
public class FluidRegistryEventJS {

    /** 跨分支暂存的待注册 builder（BLOCK 分支消费后清理）。 */
    private static final Map<String, FluidBuilderJS> PENDING = new HashMap<>();

    /** 需要桶的流体：id → 已注册 Fluid（BLOCK 分支暂存，ITEM 分支消费）。 */
    private static final Map<String, Fluid> PENDING_BUCKET_FLUIDS = new HashMap<>();

    public FluidBuilderJS create(String id) {
        FluidBuilderJS builder = new FluidBuilderJS(id);
        PENDING.put(id, builder);
        return builder;
    }

    /** 便利重载：直接配置并注册。 */
    public void create(String id, Consumer<FluidBuilderJS> consumer) {
        FluidBuilderJS builder = create(id);
        consumer.accept(builder);
    }

    /**
     * 注册流体 + 液体方块（BLOCK 分支开头调用；须在 BlockRegistryEventJS 之前）。
     */
    public static void registerAll(RegistryEvent.Register<Block> blockEvent) {
        for (FluidBuilderJS builder : PENDING.values()) {
            Fluid fluid = builder.build();
            if (!FluidRegistry.registerFluid(fluid)) {
                // 名字冲突（FluidRegistry 是全局 String 键注册表）
                com.tkisor.nekojs.NekoJS.LOGGER.error("Failed to register fluid: {}", builder.getRegistryName());
                continue;
            }
            if (builder.shouldGenerateBlock()) {
                // BlockFluidClassic 构造器自动调用 fluid.setBlock(this)，无需手动 setBlock
                BlockFluidClassic block = new BlockFluidClassic(fluid, builder.getMaterial());
                ResourceLocation rl = new ResourceLocation(builder.getRegistryName());
                block.setRegistryName(rl);
                block.setTranslationKey(rl.getNamespace() + "." + rl.getPath());
                blockEvent.getRegistry().register(block);

                if (builder.shouldGenerateBucket()) {
                    PENDING_BUCKET_FLUIDS.put(builder.getRegistryName(), fluid);
                }
            }
        }
        PENDING.clear();
    }

    /**
     * 注册桶物品（ITEM 分支末尾调用；须在 ItemRegistryEventJS.registerAll 之后）。
     */
    public static void registerBucketItems(RegistryEvent.Register<Item> itemEvent) {
        IForgeRegistry<Item> registry = itemEvent.getRegistry();
        for (Map.Entry<String, Fluid> entry : PENDING_BUCKET_FLUIDS.entrySet()) {
            ResourceLocation rl = new ResourceLocation(entry.getKey());
            Block fluidBlock = entry.getValue().getBlock();
            if (fluidBlock == null) {
                continue;
            }
            ItemBucket bucket = new ItemBucket(fluidBlock);
            ResourceLocation bucketRl = new ResourceLocation(rl.getNamespace(), rl.getPath() + "_bucket");
            bucket.setRegistryName(bucketRl);
            bucket.setTranslationKey(rl.getNamespace() + "." + rl.getPath() + "_bucket");
            registry.register(bucket);

            // 记账：让 hasBucket()/JEI 识别（不创建物品，仅标记）
            FluidRegistry.addBucketForFluid(entry.getValue());
        }
        PENDING_BUCKET_FLUIDS.clear();
    }
}
