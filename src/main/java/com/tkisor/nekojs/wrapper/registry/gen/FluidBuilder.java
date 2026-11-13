// 26.x implementation. Version guards should not appear in this file again. The 1.21.1 implementation
// is the file of the same name under versions/1.21.1/src; when changing behavior here, update it too.
//? if neoforge {
// fabric: no implementation yet; keep the whole-file guard until the port is done, then remove it
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
 * Fluid builder: one {@code event.fluid(id, cb)} call registers four side objects.
 * <ul>
 *   <li>FluidType ({@code neoforge:fluid_types}, GameData order after FLUID)</li>
 *   <li>source ({@code id}) and flowing ({@code flowing_<id>}) (FLUID registry)</li>
 *   <li>LiquidBlock (BLOCK, id = fluid id), suppressed by {@code noBlock()}</li>
 *   <li>BucketItem (ITEM, {@code <id>_bucket}), suppressed by {@code noBucket()}</li>
 * </ul>
 * This relies on the fixed GameData order FLUID -> BLOCK -> ITEM -> (modded alphabetical tail)
 * FLUID_TYPES: all four go through {@link #handleAdditionalObjects} into passes after FLUID. The
 * reference cycles (fluid to bucket and block, block to fluid, bucket to fluid) are resolved lazily
 * through each child builder's {@code Supplier} ({@link RegistryObjectBuilder#get()}), so draining
 * never builds them against each other.
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
    /** Whether the fluid can create source blocks endlessly (vanilla water defaults to off in 1.21). */
    private boolean canConvertToSource = false;

    private int slopeFindDistance = 4;
    private int levelDecreasePerBlock = 1;
    private float explosionResistance = 100.0F;
    private int tickRate = 5;

    private boolean bucket = true;
    private boolean block = true;

    private record AdditionalBuilders(RegistryObjectBuilder<Fluid> flowing,
            RegistryObjectBuilder<LiquidBlock> liquidBlock, RegistryObjectBuilder<BucketItem> bucketItem) {}

    private AdditionalBuilders additionalBuilders;
    private FluidType cachedType;

    public FluidBuilder(Identifier id) {
        super(id);
    }

    /** Defers overridable ID reads until use and publishes the sibling builders together. */
    private AdditionalBuilders additionalBuilders() {
        if (additionalBuilders != null) return additionalBuilders;
        RegistryObjectBuilder<Fluid> flowing = new RegistryObjectBuilder<>(getFlowingId()) {
            @Override
            public Fluid build() {
                return new BaseFlowingFluid.Flowing(FluidBuilder.this.properties());
            }
        };
        RegistryObjectBuilder<LiquidBlock> liquidBlock = new RegistryObjectBuilder<>(id) {
            @Override
            public LiquidBlock build() {
                return FluidBuilder.this.createLiquidBlock();
            }
        };
        RegistryObjectBuilder<BucketItem> bucketItem = new RegistryObjectBuilder<>(getBucketId()) {
            @Override
            public BucketItem build() {
                return FluidBuilder.this.createBucketItem();
            }
        };
        additionalBuilders = new AdditionalBuilders(flowing, liquidBlock, bucketItem);
        return additionalBuilders;
    }

    /** {@link TaggableBuilder}: the fluid tag (such as {@code minecraft:water}) belongs to the FLUID
     * registry. */
    @Override
    public net.minecraft.resources.ResourceKey<? extends net.minecraft.core.Registry<?>> getTagRegistry() {
        return Registries.FLUID;
    }

    @Override
    public Identifier getLocation() {
        return id;
    }

    /**
     * A fluid tag is applied to both the source and the flowing fluid, matching vanilla's
     * {@code minecraft:water} tag, which contains both {@code water} and {@code flowing_water};
     * flow checks often land on the flowing variant.
     */
    @Override
    public List<Identifier> getTagTargets() {
        return List.of(id, getFlowingId());
    }

    // ---- Data properties: the explicit setter and the JavaBean property write through the same path ----

    /** Display name. Normalized: blank means undeclared, which follows the same branch at build time
     * anyway. */
    @com.tkisor.nekojs.api.annotation.ScriptNullable
    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(@com.tkisor.nekojs.api.annotation.ScriptNullable String displayName) {
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

    /** Suppresses automatic registration of the bucket item ({@code <id>_bucket}); same write path as
     * {@code b.bucket = false}. */
    public void noBucket() {
        setBucket(false);
    }

    /** Suppresses automatic registration of the liquid block; same write path as {@code b.block = false}. */
    public void noBlock() {
        setBlock(false);
    }

    /** Id of the flowing fluid: {@code flowing_<path>}, in the same namespace. */
    public Identifier getFlowingId() {
        return Identifier.fromNamespaceAndPath(id.getNamespace(), "flowing_" + id.getPath());
    }

    /** Bucket item id: {@code <path>_bucket}. */
    public Identifier getBucketId() {
        return Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath() + "_bucket");
    }

    @Override
    public Fluid build() {
        Fluid source = new BaseFlowingFluid.Source(properties());
        return source;
    }

    /** Registers the side objects: flowing (appended in the same FLUID pass), block (BLOCK), bucket (ITEM),
     * type (FLUID_TYPES). */
    @Override
    public void handleAdditionalObjects(RegistryObjectBuilder.AdditionalObjectRegistry registry) {
        AdditionalBuilders builders = additionalBuilders();
        registry.additional(Registries.FLUID, builders.flowing.id, builders.flowing);
        if (block) {
            registry.additional(Registries.BLOCK, builders.liquidBlock.id, builders.liquidBlock);
        }
        if (bucket) {
            registry.additional(Registries.ITEM, builders.bucketItem.id, builders.bucketItem);
        }
        registry.additional(NeoForgeRegistries.FLUID_TYPES.key(), id, this::createType);
    }

    /** Creates and caches the FluidType singleton: FLUID_TYPES registration and the fluid Properties share
     * one instance. */
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
        AdditionalBuilders builders = additionalBuilders();
        BaseFlowingFluid.Properties props = new BaseFlowingFluid.Properties(
                this::createType, this::get, builders.flowing::get);
        if (bucket) {
            props.bucket(builders.bucketItem::get);
        }
        if (block) {
            props.block(builders.liquidBlock::get);
        }
        props.slopeFindDistance(slopeFindDistance)
                .levelDecreasePerBlock(levelDecreasePerBlock)
                .explosionResistance(explosionResistance)
                .tickRate(tickRate);
        return props;
    }

    /** The liquid block: it uses the source fluid, has no collision, resists explosions strongly and has
     * no loot table (the same setup as vanilla water and lava). */
    private LiquidBlock createLiquidBlock() {
        BlockBehaviour.Properties props = BlockBehaviour.Properties.of()
                .setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK, id))
                .noCollision()
                .strength(100.0F)
                .noLootTable();
        LiquidBlock liquid = new LiquidBlock((net.minecraft.world.level.material.FlowingFluid) get(), props);
        return liquid;
    }

    /** The bucket item: it uses the source fluid and is limited to one per stack. */
    private BucketItem createBucketItem() {
        net.minecraft.resources.ResourceKey<Item> key =
                net.minecraft.resources.ResourceKey.create(Registries.ITEM, getBucketId());
        Item.Properties props = new Item.Properties().setId(key).stacksTo(1);
        return new BucketItem(get(), props);
    }
}
//?}
