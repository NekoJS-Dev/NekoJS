package com.tkisor.nekojs.wrapper.registry;

import lombok.Getter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;

import java.util.function.Supplier;

/**
 * 流体注册器（{@code StartupEvents.registry('fluid')}）。
 *
 * <p>用 NeoForge {@link BaseFlowingFluid} 构建 Source/Flowing 一对，自动注册：
 * <ul>
 *   <li>{@link FluidType}（FLUID_TYPES 注册表）</li>
 *   <li>source 流体（id）+ flowing 流体（{@code flowing_<id>}）（FLUID 注册表）</li>
 *   <li>{@link LiquidBlock}（BLOCK 注册表，id = 流体 id）—— 可用 {@link #block(boolean)} 关闭</li>
 *   <li>{@link BucketItem}（ITEM 注册表，{@code <id>_bucket}）—— 可用 {@link #bucket(boolean)} 关闭</li>
 * </ul>
 *
 * <p>循环引用（fluid→bucket/block、block→fluid、bucket→fluid）经共享
 * {@link BaseFlowingFluid.Properties} 的 {@link Supplier} 懒解析，注册完成前不触发。
 * 26.x（1.21.5+）流体渲染为模型驱动（纹理走资源包模型），不提供 still/flowing 纹理 API。
 */
public class FluidBuilderJS {
    @Getter
    private final Identifier location;

    private String descriptionId;
    private int density = 1000;
    private int temperature = 300;
    private int viscosity = 1000;
    private int lightLevel = 0;
    private boolean canConvertToSource = false;

    private int slopeFindDistance = 4;
    private int levelDecreasePerBlock = 1;
    private float explosionResistance = 100.0F;
    private int tickRate = 5;

    private boolean generateBucket = true;
    private boolean generateBlock = true;

    /** 实际注册时由 FluidRegistryEventJS 注入的解析器（注册完成后再可解析）。 */
    private Supplier<Fluid> sourceSupplier;
    private Supplier<Fluid> flowingSupplier;
    private Supplier<Item> bucketSupplier;
    private Supplier<LiquidBlock> blockSupplier;

    private FluidType cachedType;

    public FluidBuilderJS(Identifier location) {
        this.location = location;
    }

    /** 显示名（作为翻译 key，如 {@code fluid.nekojs.molten_iron}）。 */
    public FluidBuilderJS displayName(String descriptionId) {
        this.descriptionId = descriptionId;
        return this;
    }

    public FluidBuilderJS density(int density) { this.density = density; return this; }
    public FluidBuilderJS temperature(int temperature) { this.temperature = temperature; return this; }
    public FluidBuilderJS viscosity(int viscosity) { this.viscosity = viscosity; return this; }
    public FluidBuilderJS lightLevel(int lightLevel) { this.lightLevel = lightLevel; return this; }

    /** 是否可无限生成源（对标原版水在 1.21 默认关闭）。默认 false。 */
    public FluidBuilderJS canConvertToSource(boolean value) { this.canConvertToSource = value; return this; }

    public FluidBuilderJS slopeFindDistance(int slopeFindDistance) { this.slopeFindDistance = slopeFindDistance; return this; }
    public FluidBuilderJS levelDecreasePerBlock(int levelDecreasePerBlock) { this.levelDecreasePerBlock = levelDecreasePerBlock; return this; }
    public FluidBuilderJS explosionResistance(float explosionResistance) { this.explosionResistance = explosionResistance; return this; }
    public FluidBuilderJS tickRate(int tickRate) { this.tickRate = tickRate; return this; }

    /** 是否自动注册桶（{@code <id>_bucket}）。默认 true。 */
    public FluidBuilderJS bucket(boolean generate) { this.generateBucket = generate; return this; }
    /** 是否自动注册液体方块（id = 流体 id）。默认 true。 */
    public FluidBuilderJS block(boolean generate) { this.generateBlock = generate; return this; }

    public boolean shouldGenerateBucket() { return generateBucket; }
    public boolean shouldGenerateBlock() { return generateBlock; }

    /** flowing 流体的 id：{@code flowing_<path>}（同 namespace）。 */
    public Identifier getFlowingLocation() {
        return Identifier.fromNamespaceAndPath(location.getNamespace(), "flowing_" + location.getPath());
    }

    /** 桶物品 id：{@code <path>_bucket}。 */
    public Identifier getBucketLocation() {
        return Identifier.fromNamespaceAndPath(location.getNamespace(), location.getPath() + "_bucket");
    }

    /**
     * 创建（并缓存）FluidType 单例。FLUID_TYPES 注册与 Properties 共用同一实例，避免重复构造。
     */
    public FluidType createType() {
        if (cachedType != null) {
            return cachedType;
        }
        FluidType.Properties props = FluidType.Properties.create()
                .density(density)
                .temperature(temperature)
                .viscosity(viscosity)
                .lightLevel(lightLevel)
                .canConvertToSource(canConvertToSource);
        if (descriptionId != null) {
            props.descriptionId(descriptionId);
        }
        cachedType = new FluidType(props);
        return cachedType;
    }

    /**
     * 由 FluidRegistryEventJS 在 FLUID 分支调用，注入注册后可解析的 Supplier。
     * 必须在 {@link #createSourceFluid()} / {@link #createFlowingFluid()} 之前调用。
     */
    public void wireSuppliers(Supplier<Fluid> source, Supplier<Fluid> flowing,
                              Supplier<Item> bucket, Supplier<LiquidBlock> block) {
        this.sourceSupplier = source;
        this.flowingSupplier = flowing;
        this.bucketSupplier = bucket;
        this.blockSupplier = block;
    }

    private BaseFlowingFluid.Properties createProperties() {
        BaseFlowingFluid.Properties props = new BaseFlowingFluid.Properties(
                this::createType, sourceSupplier, flowingSupplier);
        if (generateBucket && bucketSupplier != null) {
            props.bucket(bucketSupplier);
        }
        if (generateBlock && blockSupplier != null) {
            props.block(blockSupplier);
        }
        props.slopeFindDistance(slopeFindDistance)
                .levelDecreasePerBlock(levelDecreasePerBlock)
                .explosionResistance(explosionResistance)
                .tickRate(tickRate);
        return props;
    }

    /** source 流体（isSource=true, amount=8）。 */
    public Fluid createSourceFluid() {
        return new BaseFlowingFluid.Source(createProperties());
    }

    /** flowing 流体（isSource=false, amount=LEVEL）。 */
    public Fluid createFlowingFluid() {
        return new BaseFlowingFluid.Flowing(createProperties());
    }

    /**
     * 液体方块。引用 source 流体；noCollision + 高抗爆 + 无 loot（对标 vanilla 水/岩浆）。
     */
    public LiquidBlock createLiquidBlock(Fluid source) {
        BlockBehaviour.Properties props = BlockBehaviour.Properties.of()
                .noCollision()
                .strength(100.0F)
                .noLootTable();
        return new LiquidBlock((net.minecraft.world.level.material.FlowingFluid) source, props);
    }

    /**
     * 桶物品。引用 source 流体；stacksTo(1)。
     */
    public BucketItem createBucketItem(Fluid source) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, getBucketLocation());
        Item.Properties props = new Item.Properties().setId(key).stacksTo(1);
        return new BucketItem(source, props);
    }
}
