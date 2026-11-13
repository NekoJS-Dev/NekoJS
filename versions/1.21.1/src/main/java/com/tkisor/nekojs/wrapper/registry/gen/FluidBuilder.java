// 1.21.1 implementation, paired with the same-named 26.x file under src/ in the version tree.
// Its content equals that file evaluated at this node. Keep this file in sync when the 26.x
// side changes behavior.
package com.tkisor.nekojs.wrapper.registry.gen;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
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
 * Builds a fluid. One {@code event.fluid(id, cb)} call registers four objects:
 * <ul>
 *   <li>the FluidType ({@code neoforge:fluid_types}, which GameData orders after FLUID)</li>
 *   <li>the source fluid ({@code id}) and the flowing fluid ({@code flowing_<id>}) in FLUID</li>
 *   <li>a LiquidBlock (BLOCK, id = fluid id), suppressed by {@code noBlock()}</li>
 *   <li>a BucketItem (ITEM, {@code <id>_bucket}), suppressed by {@code noBucket()}</li>
 * </ul>
 * All four reach the pass after FLUID through {@link #handleAdditionalObjects}, following the
 * fixed GameData order FLUID, then BLOCK, then ITEM, then (modded alphabetical tail) FLUID_TYPES.
 * Circular references (fluid to bucket/block, block to fluid, bucket to fluid) resolve lazily
 * through the sub-builders' {@code Supplier} ({@link RegistryObjectBuilder#get()}), so draining
 * one builder never builds another.
 *
 * <pre>
 * event.fluid('mymod:molten_iron', b =&gt; { b.density = 3000; b.lightLevel = 15; b.noBucket() })
 * </pre>
 */
public class FluidBuilder extends RegistryObjectBuilder<Fluid> {

    private String displayName = null;
    private int density = 1000;
    private int temperature = 300;
    private int viscosity = 1000;
    private int lightLevel = 0;
    /** Whether the fluid can create infinite source blocks; off by default, like vanilla water in 1.21. */
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

    public FluidBuilder(ResourceLocation id) {
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

    // ---- Data properties: explicit setters and JavaBean properties share one write path ----

    /** Display name, normalized on write: blank means undeclared, the same branch the build path takes. */
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

    /** Skips the automatic bucket item ({@code <id>_bucket}); writes the same state as {@code b.bucket = false}. */
    public void noBucket() {
        setBucket(false);
    }

    /** Skips the automatic liquid block; writes the same state as {@code b.block = false}. */
    public void noBlock() {
        setBlock(false);
    }

    /** Returns the flowing fluid id, {@code flowing_<path>} in the same namespace. */
    public ResourceLocation getFlowingId() {
        return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "flowing_" + id.getPath());
    }

    /** Returns the bucket item id, {@code <path>_bucket}. */
    public ResourceLocation getBucketId() {
        return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), id.getPath() + "_bucket");
    }

    @Override
    public Fluid build() {
        Fluid source = new BaseFlowingFluid.Source(properties());
        if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
            com.tkisor.nekojs.client.ClientBlockRenderTypes.applyFluid(source, "translucent");
        }
        return source;
    }

    /** Registers the four companion objects: flowing (appended to the same FLUID pass), block (BLOCK),
     * bucket (ITEM) and type (FLUID_TYPES). */
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

    /** Creates and caches the single FluidType: the FLUID_TYPES registration and the fluid Properties
     * share that one instance. */
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

    /** Liquid block for the source fluid: no collision, high blast resistance and no loot table, like
     * vanilla water and lava. */
    private LiquidBlock createLiquidBlock() {
        BlockBehaviour.Properties props = BlockBehaviour.Properties.of()
                .noCollission()
                .strength(100.0F)
                .noLootTable();
        LiquidBlock liquid = new LiquidBlock((net.minecraft.world.level.material.FlowingFluid) get(), props);
        if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
            com.tkisor.nekojs.client.ClientBlockRenderTypes.apply(liquid, "translucent");
        }
        return liquid;
    }

    /** Bucket item for the source fluid; stacks to 1. */
    private BucketItem createBucketItem() {
        Item.Properties props = new Item.Properties().stacksTo(1);
        return new BucketItem(get(), props);
    }
}
