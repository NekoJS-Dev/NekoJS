package com.tkisor.nekojs.bindings.static_access;

import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * {@code Capabilities} 静态绑定：创建标准 capability 的简单 backing 实现，
 * 供 {@code CapabilityEvents.register} 的 provider 返回。
 *
 * <p>MVP 版本仅覆盖 item / energy / fluid 三类标准能力；实例无上下文（所有同类型
 * 方块实体共享），NBT 持久化由实现自带（如 {@link ItemStackHandler} 的 serialize）。
 */
public class CapabilitiesJS {
    /** 创建 N 格物品栏（{@code ItemStackHandler}）。 */
    public ItemStackHandler itemHandler(int size) {
        return new ItemStackHandler(size);
    }

    /** 创建能量存储（容量 / 最大输入 / 最大输出 FE/t）。 */
    public EnergyStorage energyStorage(int capacity, int maxReceive, int maxExtract) {
        return new EnergyStorage(capacity, maxReceive, maxExtract);
    }

    /** 创建单槽流体罐（容量 mB）。 */
    public FluidTank fluidTank(int capacity) {
        return new FluidTank(capacity);
    }
}
