package com.tkisor.nekojs.wrapper.registry;

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
    public FluidBuilderJS stillTexture(String id) {
        this.stillTexture = parseResource(id, stillTexture);
        return this;
    }

    /** 流动纹理（如 {@code 'minecraft:blocks/water_flow'}）。 */
    public FluidBuilderJS flowingTexture(String id) {
        this.flowingTexture = parseResource(id, flowingTexture);
        return this;
    }

    /** 翻译 key（如 {@code fluid.nekojs.molten_iron}）。 */
    public FluidBuilderJS translationKey(String key) {
        this.translationKey = key;
        return this;
    }

    public FluidBuilderJS density(int density) { this.density = density; return this; }
    public FluidBuilderJS temperature(int temperature) { this.temperature = temperature; return this; }
    public FluidBuilderJS viscosity(int viscosity) { this.viscosity = viscosity; return this; }

    /** 发光等级（0-15）。 */
    public FluidBuilderJS luminosity(int luminosity) {
        this.luminosity = Math.max(0, Math.min(15, luminosity));
        return this;
    }

    /** 是否气态（密度 < 0 语义，向上漂浮）。 */
    public FluidBuilderJS gaseous(boolean gaseous) { this.gaseous = gaseous; return this; }

    /** 流体颜色（ARGB int）。 */
    public FluidBuilderJS color(int color) { this.color = color; return this; }

    /** 液体方块材质（{@code 'water'} / {@code 'lava'}，走 BlockBuilder 同款 switch）。默认 water。 */
    public FluidBuilderJS material(String materialName) {
        this.material = switch (materialName.toLowerCase()) {
            case "water" -> Material.WATER;
            case "lava" -> Material.LAVA;
            default -> Material.WATER;
        };
        return this;
    }

    /** 是否自动注册液体方块（{@code <id>}）。默认 true。 */
    public FluidBuilderJS block(boolean generate) { this.generateBlock = generate; return this; }

    /** 是否自动注册桶（{@code <id>_bucket}）。默认 true；block(false) 时强制 false。 */
    public FluidBuilderJS bucket(boolean generate) { this.generateBucket = generate; return this; }

    public boolean shouldGenerateBlock() { return generateBlock; }
    public boolean shouldGenerateBucket() { return generateBlock && generateBucket; }

    public String getRegistryName() { return registryName; }
    public Material getMaterial() { return material; }

    /**
     * 构建 1.12.2 {@link Fluid} 实例（不注册）。流体名用 registryName 的 path 部分
     * （FluidRegistry 是全局 String 键注册表）。
     */
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
