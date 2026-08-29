// TODO(loader-port): deferred to the LoaderBridge fabric port
//? if neoforge {
package com.tkisor.nekojs.wrapper.registry.gen;

//? if >=26 {
import com.tkisor.nekojs.wrapper.registry.TaggableBuilder;
import java.util.List;
//?}
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import java.util.function.Supplier;

/**
 * 流体 builder（ADR-0005 四子连带注册）：一次 {@code event.fluid(id, cb)} 注册
 * <ul>
 *   <li>FluidType（{@code neoforge:fluid_types}，GameData 序在 FLUID 之后）</li>
 *   <li>source（{@code id}）+ flowing（{@code flowing_<id>}）（FLUID 注册表）</li>
 *   <li>LiquidBlock（BLOCK，id = 流体 id）—— {@code noBlock()} 抑制</li>
 *   <li>BucketItem（ITEM，{@code <id>_bucket}）—— {@code noBucket()} 抑制</li>
 * </ul>
 * 依赖 GameData 固定序 FLUID → BLOCK → ITEM →（modded 字母尾）FLUID_TYPES：
 * 四子全部经 {@link #handleAdditionalObjects} 投给 FLUID 之后的 pass；
 * 循环引用（fluid→bucket/block、block→fluid、bucket→fluid）经子 builder 的
 * {@code Supplier} 懒解析（{@link RegistryObjectBuilder#get()}），抽干期不触发互构。
 *
 * <pre>
 * event.fluid('mymod:molten_iron', b =&gt; { b.density = 3000; b.lightLevel = 15; b.noBucket() })
 * </pre>
 */
//? if >=26 {
public class FluidBuilder extends RegistryObjectBuilder<Fluid> implements TaggableBuilder<FluidBuilder> {
//?} else {
/*public class FluidBuilder extends RegistryObjectBuilder<Fluid> {
*///?}

    public String displayName = null;
    public int density = 1000;
    public int temperature = 300;
    public int viscosity = 1000;
    public int lightLevel = 0;
    /** 是否可无限生成源（对标原版水在 1.21 默认关闭）。 */
    public boolean canConvertToSource = false;

    public int slopeFindDistance = 4;
    public int levelDecreasePerBlock = 1;
    public float explosionResistance = 100.0F;
    public int tickRate = 5;

    public boolean bucket = true;
    public boolean block = true;

    private final RegistryObjectBuilder<Fluid> flowing;
    private final RegistryObjectBuilder<LiquidBlock> liquidBlock;
    private final RegistryObjectBuilder<BucketItem> bucketItem;

    private FluidType cachedType;

    public FluidBuilder(Identifier id) {
        super(id);
        this.flowing = new RegistryObjectBuilder<>(getFlowingId()) {
            @Override
            public Fluid build() {
                return new BaseFlowingFluid.Flowing(FluidBuilder.this.properties());
            }
        };
        this.liquidBlock = new RegistryObjectBuilder<>(id) {
            @Override
            public LiquidBlock build() {
                return FluidBuilder.this.createLiquidBlock();
            }
        };
        this.bucketItem = new RegistryObjectBuilder<>(getBucketId()) {
            @Override
            public BucketItem build() {
                return FluidBuilder.this.createBucketItem();
            }
        };
//? if >=26 {
    }

    /** {@link TaggableBuilder}：流体 tag（如 {@code minecraft:water}）归属 FLUID 注册表。 */
    @Override
    public net.minecraft.resources.ResourceKey<? extends net.minecraft.core.Registry<?>> getTagRegistry() {
        return Registries.FLUID;
    }

    @Override
    public Identifier getLocation() {
        return id;
    }

    /**
     * 流体 tag 同时打 source 与 flowing 两个流体（对标原版 {@code minecraft:water} tag
     * 同时含 {@code water} 与 {@code flowing_water}——流动性检查常落在 flowing 变体上）。
     */
    @Override
    public List<Identifier> getTagTargets() {
        return List.of(id, getFlowingId());
//?}
    }

    /** 抑制自动桶物品（{@code <id>_bucket}）连带注册。 */
    public void noBucket() {
        this.bucket = false;
    }

    /** 抑制自动液体方块连带注册。 */
    public void noBlock() {
        this.block = false;
    }

    /** flowing 流体的 id：{@code flowing_<path>}（同 namespace）。 */
    public Identifier getFlowingId() {
        return Identifier.fromNamespaceAndPath(id.getNamespace(), "flowing_" + id.getPath());
    }

    /** 桶物品 id：{@code <path>_bucket}。 */
    public Identifier getBucketId() {
        return Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath() + "_bucket");
    }

    @Override
    public Fluid build() {
        Fluid source = new BaseFlowingFluid.Source(properties());
//? if <26 {
/*        if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
            com.tkisor.nekojs.client.ClientBlockRenderTypes.applyFluid(source, "translucent");
        }
*///?}
        return source;
    }

    /** 四子连带注册：flowing（FLUID 同 pass 追加）/ block（BLOCK）/ bucket（ITEM）/ type（FLUID_TYPES）。 */
    @Override
    public void handleAdditionalObjects(RegistryObjectBuilder.AdditionalObjectRegistry registry) {
        registry.additional(Registries.FLUID, flowing.id, flowing);
        if (block) {
            registry.additional(Registries.BLOCK, liquidBlock.id, liquidBlock);
        }
        if (bucket) {
            registry.additional(Registries.ITEM, bucketItem.id, bucketItem);
        }
        registry.additional(NeoForgeRegistries.FLUID_TYPES.key(), id, this::createType);
    }

    /** 创建（并缓存）FluidType 单例：FLUID_TYPES 注册与流体 Properties 共用同一实例。 */
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
        if (displayName != null && !displayName.isBlank()) {
            props.descriptionId(displayName);
        }
        cachedType = new FluidType(props);
        return cachedType;
    }

    private BaseFlowingFluid.Properties properties() {
        BaseFlowingFluid.Properties props = new BaseFlowingFluid.Properties(
                this::createType, this::get, flowing::get);
        if (bucket) {
            props.bucket(bucketItem::get);
        }
        if (block) {
            props.block(liquidBlock::get);
        }
        props.slopeFindDistance(slopeFindDistance)
                .levelDecreasePerBlock(levelDecreasePerBlock)
                .explosionResistance(explosionResistance)
                .tickRate(tickRate);
        return props;
    }

    /** 液体方块：引用 source 流体；noCollision + 高抗爆 + 无 loot（对标 vanilla 水/岩浆）。 */
    private LiquidBlock createLiquidBlock() {
        BlockBehaviour.Properties props = BlockBehaviour.Properties.of()
//? if >=26 {
                .setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK, id))
                .noCollision()
//?} else {
/*                .noCollission()
*///?}
                .strength(100.0F)
                .noLootTable();
        LiquidBlock liquid = new LiquidBlock((net.minecraft.world.level.material.FlowingFluid) get(), props);
//? if <26 {
/*        if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
            com.tkisor.nekojs.client.ClientBlockRenderTypes.apply(liquid, "translucent");
        }
*///?}
        return liquid;
    }

    /** 桶物品：引用 source 流体；stacksTo(1)。 */
    private BucketItem createBucketItem() {
//? if >=26 {
        net.minecraft.resources.ResourceKey<Item> key =
                net.minecraft.resources.ResourceKey.create(Registries.ITEM, getBucketId());
        Item.Properties props = new Item.Properties().setId(key).stacksTo(1);
//?} else {
/*        Item.Properties props = new Item.Properties().stacksTo(1);
*///?}
        return new BucketItem(get(), props);
    }
}
//?}
