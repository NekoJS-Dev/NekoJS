package com.tkisor.nekojs.wrapper.event.registry;

import com.tkisor.nekojs.wrapper.registry.FluidBuilderJS;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * 流体注册事件对象（{@code StartupEvents.registry('fluid')}）。
 *
 * <p>流体类型与流体本体分属两个 registry（{@code FLUID_TYPES} / {@code FLUID}），
 * {@code RegisterEvent} 按 registry 分别触发。两个分支都会 post 本事件（脚本
 * {@code create} 按 id 覆盖去重，幂等），分支各自注册对应的条目。
 */
public class FluidRegistryEventJS {

    private static final Map<Identifier, FluidBuilderJS> PENDING = new HashMap<>();

    public FluidBuilderJS create(Identifier id) {
        FluidBuilderJS builder = new FluidBuilderJS(id);
        PENDING.put(id, builder);
        return builder;
    }

    /** 注册流体类型（{@code FLUID_TYPES} 分支）。 */
    public static void registerTypes(RegisterEvent event) {
        for (FluidBuilderJS builder : PENDING.values()) {
            event.register(NeoForgeRegistries.FLUID_TYPES.key(), builder.getLocation(), builder::createType);
        }
    }

    /** 注册流体本体（{@code FLUID} 分支，最后清理 pending）。 */
    public static void registerFluids(RegisterEvent event) {
        for (FluidBuilderJS builder : PENDING.values()) {
            FluidType type = builder.createType();
            event.register(Registries.FLUID, builder.getLocation(), () -> builder.createFluid(type));
        }
        PENDING.clear();
    }
}
