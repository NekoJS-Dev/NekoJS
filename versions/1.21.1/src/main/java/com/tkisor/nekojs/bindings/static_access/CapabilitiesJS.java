// 1.21.1 实现，与版本树 src/ 下的同名 26.x 文件成对。内容等于那份文件在本节点求值后的形态
//（可用 tools/extract_evaluated.py 重新提取核对）；26.x 侧行为变更时须同步本文件。
package com.tkisor.nekojs.bindings.static_access;

import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * {@code Capabilities} 静态绑定：创建标准 capability 实例（26.x transfer API 类型），
 * 供 {@code CapabilityEvents.register} 的 provider 返回。
 *
 * <p>26.x 的 capability 常量类型为 transfer API（{@code Capabilities.Item.BLOCK} →
 * {@code ResourceHandler<ItemResource>} 等），本类把经典 backing 实现
 * （ItemStackHandler / EnergyStorage / FluidTank，自带 NBT 序列化）桥接为对应类型。
 *
 * <p>桥接语义：insert/extract 直接执行（不参与外层事务回滚，等价于每次调用立即提交）。
 *
 * <p>旧接口（ItemStackHandler 等）在 26.x 标记为待删除——桥接正是为过渡期提供支持，
 * 新版 transfer API 的本地实现可后续替换。
 */
public class CapabilitiesJS {
    /** 创建 N 格物品栏（内部 {@link ItemStackHandler}，暴露为 transfer API 类型）。 */
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
