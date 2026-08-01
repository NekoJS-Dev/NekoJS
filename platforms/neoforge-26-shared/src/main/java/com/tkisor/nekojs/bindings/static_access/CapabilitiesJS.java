package com.tkisor.nekojs.bindings.static_access;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

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
@SuppressWarnings("removal")
public class CapabilitiesJS {
    /** 创建 N 格物品栏（内部 {@link ItemStackHandler}，暴露为 transfer API 类型）。 */
    public ResourceHandler<ItemResource> itemHandler(int size) {
        return new ItemResourceHandlerBridge(new ItemStackHandler(size));
    }

    /** 创建能量存储（容量 / 最大输入 / 最大输出 FE/t）。 */
    public EnergyHandler energyStorage(int capacity, int maxReceive, int maxExtract) {
        return new EnergyHandlerBridge(new EnergyStorage(capacity, maxReceive, maxExtract));
    }

    /** 创建单槽流体罐（容量 mB）。 */
    public ResourceHandler<FluidResource> fluidTank(int capacity) {
        return new FluidResourceHandlerBridge(new FluidTank(capacity));
    }

    /** IItemHandler → ResourceHandler&lt;ItemResource&gt; 正向桥接。 */
    private record ItemResourceHandlerBridge(IItemHandler handler) implements ResourceHandler<ItemResource> {
        @Override
        public int size() {
            return handler.getSlots();
        }

        @Override
        public ItemResource getResource(int slot) {
            return ItemResource.of(handler.getStackInSlot(slot));
        }

        @Override
        public long getAmountAsLong(int slot) {
            return handler.getStackInSlot(slot).getCount();
        }

        @Override
        public long getCapacityAsLong(int slot, ItemResource resource) {
            return handler.getSlotLimit(slot);
        }

        @Override
        public boolean isValid(int slot, ItemResource resource) {
            return handler.isItemValid(slot, resource.toStack(1));
        }

        @Override
        public int insert(int slot, ItemResource resource, int amount, TransactionContext context) {
            ItemStack remaining = handler.insertItem(slot, resource.toStack(amount), false);
            return amount - remaining.getCount();
        }

        @Override
        public int extract(int slot, ItemResource resource, int amount, TransactionContext context) {
            return handler.extractItem(slot, amount, false).getCount();
        }
    }

    /** IEnergyStorage → EnergyHandler 正向桥接。 */
    private record EnergyHandlerBridge(IEnergyStorage storage) implements EnergyHandler {
        @Override
        public long getAmountAsLong() {
            return storage.getEnergyStored();
        }

        @Override
        public long getCapacityAsLong() {
            return storage.getMaxEnergyStored();
        }

        @Override
        public int insert(int maxAmount, TransactionContext context) {
            return storage.receiveEnergy(maxAmount, false);
        }

        @Override
        public int extract(int maxAmount, TransactionContext context) {
            return storage.extractEnergy(maxAmount, false);
        }
    }

    /** IFluidHandler → ResourceHandler&lt;FluidResource&gt; 正向桥接。 */
    private record FluidResourceHandlerBridge(IFluidHandler handler) implements ResourceHandler<FluidResource> {
        @Override
        public int size() {
            return handler.getTanks();
        }

        @Override
        public FluidResource getResource(int tank) {
            return FluidResource.of(handler.getFluidInTank(tank));
        }

        @Override
        public long getAmountAsLong(int tank) {
            return handler.getFluidInTank(tank).getAmount();
        }

        @Override
        public long getCapacityAsLong(int tank, FluidResource resource) {
            return handler.getTankCapacity(tank);
        }

        @Override
        public boolean isValid(int tank, FluidResource resource) {
            return handler.isFluidValid(tank, new FluidStack(resource.value(), 1));
        }

        @Override
        public int insert(int tank, FluidResource resource, int amount, TransactionContext context) {
            return handler.fill(new FluidStack(resource.value(), amount), IFluidHandler.FluidAction.EXECUTE);
        }

        @Override
        public int extract(int tank, FluidResource resource, int amount, TransactionContext context) {
            return handler.drain(new FluidStack(resource.value(), amount), IFluidHandler.FluidAction.EXECUTE).getAmount();
        }
    }
}
