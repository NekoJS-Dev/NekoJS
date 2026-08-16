package com.tkisor.nekojs.wrapper.registry;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import net.minecraft.block.material.Material;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;

/**
 * 1.12.2 流体注册器（{@code StartupEvents.registry('fluid')}）。
 *
 * <p>1.12.2 的流体走 {@link FluidRegistry} 静态注册表（不是 Forge 注册表，没有
 * {@code RegistryEvent.Register<Fluid>}）。本 builder 在 BLOCK 注册分支中完成
 * {@code registerFluid}，并自动注册 {@code BlockFluidClassic} 液体方块与
 * {@code ItemBucket} 桶物品（{@code <id>_bucket}）。
 *
 * <p>注意：1.12.2 的桶（{@code ItemBucket}）引用方块而非流体，因此
 * {@code block(false)} 时强制 {@code bucket(false)}。
 */
@Doc("Builder for registering a new fluid; obtain it from RegistryEvents.fluid.create(id).")
@Doc("Registers the fluid, its liquid block, and a bucket item automatically during the block/item registry phases.")
public class FluidBuilderJS {
    private final String registryName;

    private ResourceLocation stillTexture = new ResourceLocation("minecraft", "blocks/water_still");
    private ResourceLocation flowingTexture = new ResourceLocation("minecraft", "blocks/water_flow");
    private String translationKey;
    private int density = 1000;
    private int temperature = 300;
    private int viscosity = 1000;
    private int luminosity = 0;
    private boolean gaseous = false;
    private int color = 0xFFFFFFFF;
    private Material material = Material.WATER;
    private boolean generateBlock = true;
    private boolean generateBucket = true;

    public FluidBuilderJS(String registryName) {
        this.registryName = registryName;
    }

    /** 静止纹理（如 {@code 'minecraft:blocks/water_still'}）。 */
    @Doc("Sets the still texture of the fluid.")
    @Param(name = "id", value = "texture id like 'minecraft:blocks/water_still'; namespace defaults to minecraft")
    @Return("this builder, for chaining")
    public FluidBuilderJS stillTexture(String id) {
        this.stillTexture = parseResource(id, stillTexture);
        return this;
    }

    /** 流动纹理（如 {@code 'minecraft:blocks/water_flow'}）。 */
    @Doc("Sets the flowing texture of the fluid.")
    @Param(name = "id", value = "texture id like 'minecraft:blocks/water_flow'; namespace defaults to minecraft")
    @Return("this builder, for chaining")
    public FluidBuilderJS flowingTexture(String id) {
        this.flowingTexture = parseResource(id, flowingTexture);
        return this;
    }

    /** 翻译 key（如 {@code fluid.nekojs.molten_iron}）。 */
    @Doc("Sets an explicit translation key; defaults to '<namespace>.<path>'.")
    @Param(name = "key", value = "translation key like 'fluid.mymod.molten_iron'")
    @Return("this builder, for chaining")
    public FluidBuilderJS translationKey(String key) {
        this.translationKey = key;
        return this;
    }

    /** 密度。 */
    @Doc("Sets the fluid density; negative densities make the fluid lighter than water.")
    @Param(name = "density", value = "density value; water is 1000")
    @Return("this builder, for chaining")
    public FluidBuilderJS density(int density) { this.density = density; return this; }

    /** 温度（开尔文）。 */
    @Doc("Sets the fluid temperature in Kelvin; water is 300, lava is 3000.")
    @Param(name = "temperature", value = "temperature in Kelvin")
    @Return("this builder, for chaining")
    public FluidBuilderJS temperature(int temperature) { this.temperature = temperature; return this; }

    /** 粘度。 */
    @Doc("Sets the fluid viscosity; higher values flow more slowly.")
    @Param(name = "viscosity", value = "viscosity value; water is 1000")
    @Return("this builder, for chaining")
    public FluidBuilderJS viscosity(int viscosity) { this.viscosity = viscosity; return this; }

    /** 发光等级（0-15）。 */
    @Doc("Sets the fluid's emitted light level.")
    @Param(name = "luminosity", value = "light level from 0 to 15; clamped")
    @Return("this builder, for chaining")
    public FluidBuilderJS luminosity(int luminosity) {
        this.luminosity = Math.max(0, Math.min(15, luminosity));
        return this;
    }

    /** 是否气态（密度 < 0 语义，向上漂浮）。 */
    @Doc("Marks the fluid as gaseous so it rises instead of sinking.")
    @Param(name = "gaseous", value = "true if the fluid is a gas")
    @Return("this builder, for chaining")
    public FluidBuilderJS gaseous(boolean gaseous) { this.gaseous = gaseous; return this; }

    /** 流体颜色（ARGB int）。 */
    @Doc("Sets the fluid color tint.")
    @Param(name = "color", value = "color as an ARGB integer like 0xFFFF6600")
    @Return("this builder, for chaining")
    public FluidBuilderJS color(int color) { this.color = color; return this; }

    /** 液体方块材质（{@code 'water'} / {@code 'lava'}，走 BlockBuilder 同款 switch）。默认 water。 */
    @Doc("Sets the material of the fluid's liquid block.")
    @Param(name = "materialName", value = "'water' or 'lava'; anything else falls back to water")
    @Return("this builder, for chaining")
    public FluidBuilderJS material(String materialName) {
        this.material = switch (materialName.toLowerCase()) {
            case "water" -> Material.WATER;
            case "lava" -> Material.LAVA;
            default -> Material.WATER;
        };
        return this;
    }

    /** 是否自动注册液体方块（{@code <id>}）。默认 true。 */
    @Doc("Toggles automatic registration of the fluid's liquid block.")
    @Param(name = "generate", value = "true to generate the block; default true")
    @Return("this builder, for chaining")
    public FluidBuilderJS block(boolean generate) { this.generateBlock = generate; return this; }

    /** 是否自动注册桶（{@code <id>_bucket}）。默认 true；block(false) 时强制 false。 */
    @Doc("Toggles automatic registration of the fluid's bucket item.")
    @Doc("Forced off when block(false) — 1.12.2 buckets reference the block, not the fluid.")
    @Param(name = "generate", value = "true to generate the bucket; default true")
    @Return("this builder, for chaining")
    public FluidBuilderJS bucket(boolean generate) { this.generateBucket = generate; return this; }

    /** 是否生成液体方块。 */
    @Doc("Whether the liquid block will be generated.")
    @Return("true unless block(false) was called")
    public boolean shouldGenerateBlock() { return generateBlock; }

    /** 是否生成桶。 */
    @Doc("Whether the bucket item will be generated.")
    @Return("true only if both block and bucket generation are enabled")
    public boolean shouldGenerateBucket() { return generateBlock && generateBucket; }

    /** 注册名。 */
    @Doc("Gets the registry name of the fluid being built.")
    @Return("the registry name string")
    public String getRegistryName() { return registryName; }

    /** 液体方块材质。 */
    @Doc("Gets the material of the fluid's liquid block.")
    @Return("the Material instance")
    public Material getMaterial() { return material; }

    /**
     * 构建 1.12.2 {@link Fluid} 实例（不注册）。流体名用 registryName 的 path 部分
     * （FluidRegistry 是全局 String 键注册表）。
     */
    @Doc("Builds the Fluid instance; does not register it.")
    @Doc("Registration happens automatically during the block registry phase.")
    @Return("the configured fluid")
    @SuppressWarnings("deprecation")
    public Fluid build() {
        ResourceLocation rl = new ResourceLocation(registryName);
        Fluid fluid = new Fluid(rl.getPath(), stillTexture, flowingTexture);
        fluid.setDensity(density)
                .setTemperature(temperature)
                .setViscosity(viscosity)
                .setLuminosity(luminosity)
                .setGaseous(gaseous)
                .setColor(color)
                .setUnlocalizedName(translationKey != null
                        ? translationKey
                        : rl.getNamespace() + "." + rl.getPath());
        return fluid;
    }

    private static ResourceLocation parseResource(String id, ResourceLocation fallback) {
        if (id == null || id.isBlank()) return fallback;
        return id.contains(":") ? new ResourceLocation(id) : new ResourceLocation("minecraft", id);
    }
}
