package com.tkisor.nekojs.wrapper.registry;

import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.neoforge.fluids.FluidType;

/**
 * 流体注册器（{@code StartupEvents.registry('fluid')}）。
 *
 * <p>26.x（1.21.5+）的流体渲染为模型驱动（纹理走资源包模型），因此不提供
 * still/flowing 纹理 API；流体类型通过 {@link FluidType} 注册到
 * {@code NeoForgeRegistries.FLUID_TYPES}。实现为简单流体：单一源流体、无流动、
 * 无流体方块（{@code createLegacyBlock} 返回空气）。
 */
public class FluidBuilderJS {
    @Getter
    private final net.minecraft.resources.Identifier location;

    private String descriptionId;
    private int density = 1000;
    private int temperature = 300;
    private int viscosity = 1000;
    private int lightLevel = 0;

    public FluidBuilderJS(net.minecraft.resources.Identifier location) {
        this.location = location;
    }

    /** 显示名（作为翻译 key，如 {@code fluid.nekojs.molten_iron}；脚本可配合 lang 事件提供文本）。 */
    public FluidBuilderJS displayName(String descriptionId) {
        this.descriptionId = descriptionId;
        return this;
    }

    public FluidBuilderJS density(int density) { this.density = density; return this; }
    public FluidBuilderJS temperature(int temperature) { this.temperature = temperature; return this; }
    public FluidBuilderJS viscosity(int viscosity) { this.viscosity = viscosity; return this; }
    public FluidBuilderJS lightLevel(int lightLevel) { this.lightLevel = lightLevel; return this; }

    public FluidType createType() {
        FluidType.Properties props = FluidType.Properties.create()
                .density(density)
                .temperature(temperature)
                .viscosity(viscosity)
                .lightLevel(lightLevel);
        if (descriptionId != null) {
            props.descriptionId(descriptionId);
        }
        return new FluidType(props);
    }

    public Fluid createFluid(FluidType type) {
        return new Fluid() {
            @Override
            public Item getBucket() {
                return Items.AIR;
            }

            @Override
            public boolean canBeReplacedWith(FluidState state, BlockGetter level, BlockPos pos, Fluid fluid, Direction direction) {
                return false;
            }

            @Override
            public Vec3 getFlow(BlockGetter level, BlockPos pos, FluidState state) {
                return Vec3.ZERO;
            }

            @Override
            public int getTickDelay(LevelReader level) {
                return 5;
            }

            @Override
            public float getExplosionResistance() {
                return 100.0F;
            }

            @Override
            public float getHeight(FluidState state, BlockGetter level, BlockPos pos) {
                return 0.9F;
            }

            @Override
            public float getOwnHeight(FluidState state) {
                return 0.9F;
            }

            @Override
            public BlockState createLegacyBlock(FluidState state) {
                return Blocks.AIR.defaultBlockState();
            }

            @Override
            public VoxelShape getShape(FluidState state, BlockGetter level, BlockPos pos) {
                return Shapes.empty();
            }

            @Override
            public boolean isSource(FluidState state) {
                return true;
            }

            @Override
            public int getAmount(FluidState state) {
                return 8;
            }

            @Override
            public FluidType getFluidType() {
                return type;
            }
        };
    }
}
