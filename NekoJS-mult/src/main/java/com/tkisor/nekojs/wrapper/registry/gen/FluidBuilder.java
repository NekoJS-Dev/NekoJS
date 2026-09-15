// 26.x 实现，本文件不应再出现版本守卫。1.21.1 的实现是 versions/1.21.1/src 下的同名文件，
// 改本文件行为时须同步它。
//? if neoforge {
// TODO(fabric): fabric 侧还没有对应实现，整文件守卫等移植完成后去掉
package com.tkisor.nekojs.wrapper.registry.gen;

import com.tkisor.nekojs.wrapper.registry.TaggableBuilder;
import java.util.List;
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
public class FluidBuilder extends RegistryObjectBuilder<Fluid> implements TaggableBuilder<FluidBuilder> {

    private String displayName = null;
    private int density = 1000;
    private int temperature = 300;
    private int viscosity = 1000;
    private int lightLevel = 0;
    /** 是否可无限生成源（对标原版水在 1.21 默认关闭）。 */
    private boolean canConvertToSource = false;

    private int slopeFindDistance = 4;
    private int levelDecreasePerBlock = 1;
    private float explosionResistance = 100.0F;
    private int tickRate = 5;

    private boolean bucket = true;
    private boolean block = true;

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
    }

    // ---- 数据属性：显式 setter 与 JavaBean property 同一写入点（ticket 15） ----

    /** 显示名（已归一化：空白视为未声明——build 期本来就走同一分支）。 */
    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName == null || displayName.isBlank() ? null : displayName;
    }

    public int getDensity() {
        return density;
    }

    public void setDensity(int density) {
        this.density = density;
    }

    public int getTemperature() {
        return temperature;
    }

    public void setTemperature(int temperature) {
        this.temperature = temperature;
    }

    public int getViscosity() {
        return viscosity;
    }

    public void setViscosity(int viscosity) {
        this.viscosity = viscosity;
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

    public boolean isCanConvertToSource() {
        return canConvertToSource;
    }

    public void setCanConvertToSource(boolean canConvertToSource) {
        this.canConvertToSource = canConvertToSource;
    }

    public int getSlopeFindDistance() {
        return slopeFindDistance;
    }

    public void setSlopeFindDistance(int slopeFindDistance) {
        if (slopeFindDistance < 0) {
            throw new IllegalArgumentException("slopeFindDistance must be >= 0 but got " + slopeFindDistance);
        }
        this.slopeFindDistance = slopeFindDistance;
    }

    public int getLevelDecreasePerBlock() {
        return levelDecreasePerBlock;
    }

    public void setLevelDecreasePerBlock(int levelDecreasePerBlock) {
        if (levelDecreasePerBlock < 0) {
            throw new IllegalArgumentException("levelDecreasePerBlock must be >= 0 but got " + levelDecreasePerBlock);
        }
        this.levelDecreasePerBlock = levelDecreasePerBlock;
    }

    public float getExplosionResistance() {
        return explosionResistance;
    }

    public void setExplosionResistance(float explosionResistance) {
        this.explosionResistance = explosionResistance;
    }

    public int getTickRate() {
        return tickRate;
    }

    public void setTickRate(int tickRate) {
        if (tickRate < 1) {
            throw new IllegalArgumentException("tickRate must be >= 1 but got " + tickRate);
        }
        this.tickRate = tickRate;
    }

    public boolean isBucket() {
        return bucket;
    }

    public void setBucket(boolean bucket) {
        this.bucket = bucket;
    }

    public boolean isBlock() {
        return block;
    }

    public void setBlock(boolean block) {
        this.block = block;
    }

    /** 抑制自动桶物品（{@code <id>_bucket}）连带注册（与 {@code b.bucket = false} 同一写入点）。 */
    public void noBucket() {
        setBucket(false);
    }

    /** 抑制自动液体方块连带注册（与 {@code b.block = false} 同一写入点）。 */
    public void noBlock() {
        setBlock(false);
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
                .setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK, id))
                .noCollision()
                .strength(100.0F)
                .noLootTable();
        LiquidBlock liquid = new LiquidBlock((net.minecraft.world.level.material.FlowingFluid) get(), props);
        return liquid;
    }

    /** 桶物品：引用 source 流体；stacksTo(1)。 */
    private BucketItem createBucketItem() {
        net.minecraft.resources.ResourceKey<Item> key =
                net.minecraft.resources.ResourceKey.create(Registries.ITEM, getBucketId());
        Item.Properties props = new Item.Properties().setId(key).stacksTo(1);
        return new BucketItem(get(), props);
    }
}
//?}
