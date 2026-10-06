package com.tkisor.nekojs.bindings.static_access;

import com.tkisor.nekojs.fabric.FabricCapabilities;
import com.tkisor.nekojs.fabric.capability.FabricEnergyStorage;
import com.tkisor.nekojs.fabric.capability.FabricFluidStorage;
import com.tkisor.nekojs.fabric.capability.FabricItemStorage;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.fabricmc.fabric.api.lookup.v1.entity.EntityApiLookup;
import net.fabricmc.fabric.api.lookup.v1.item.ItemApiLookup;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Native Fabric storage factories and capability lookups exposed to scripts. */
public class CapabilitiesJS {
    public FabricItemStorage itemHandler(int size) {
        return itemHandler(size, () -> {});
    }

    public FabricItemStorage itemHandler(int size, Runnable onChange) {
        return new FabricItemStorage(size, onChange);
    }

    public FabricEnergyStorage energyStorage(int capacity, int maxReceive, int maxExtract) {
        return energyStorage(capacity, maxReceive, maxExtract, () -> {});
    }

    public FabricEnergyStorage energyStorage(int capacity, int maxReceive, int maxExtract, Runnable onChange) {
        return new FabricEnergyStorage(capacity, maxReceive, maxExtract, onChange);
    }

    public FabricFluidStorage fluidTank(int capacity) {
        return fluidTank(capacity, () -> {});
    }

    public FabricFluidStorage fluidTank(int capacity, Runnable onChange) {
        if (capacity < 0) {
            throw new IllegalArgumentException("[NEKO-4012] Fluid capacity must be non-negative: " + capacity);
        }
        return new FabricFluidStorage(capacity * (FluidConstants.BUCKET / 1000), onChange);
    }

    public BlockApiLookup<?, Direction> blockLookup(String capability) {
        return FabricCapabilities.blockLookup(capability);
    }

    public EntityApiLookup<?, Direction> entityLookup(String capability) {
        return FabricCapabilities.entityLookup(capability);
    }

    public ItemApiLookup<?, ContainerItemContext> itemLookup(String capability) {
        return FabricCapabilities.itemLookup(capability);
    }

    public Object getBlock(Level level, BlockPos position, String capability, Direction direction) {
        return FabricCapabilities.getBlock(level, position, capability, direction);
    }

    public Object getEntity(Entity entity, String capability, Direction direction) {
        return FabricCapabilities.getEntity(entity, capability, direction);
    }

    public Object getItem(ItemStack stack, String capability, ContainerItemContext context) {
        return FabricCapabilities.getItem(stack, capability, context);
    }
}
