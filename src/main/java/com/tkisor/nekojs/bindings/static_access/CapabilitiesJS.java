//? if neoforge {
package com.tkisor.nekojs.bindings.static_access;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

import java.util.Objects;

/** Creates native transactional capability storage. Owners retain and serialize each instance. */
public class CapabilitiesJS {
    public ResourceHandler<ItemResource> itemHandler(int size) {
        return itemHandler(size, () -> {});
    }

    public ResourceHandler<ItemResource> itemHandler(int size, Runnable onChange) {
        requireNonNegative("size", size);
        Objects.requireNonNull(onChange);
        return new ItemStacksResourceHandler(size) {
            @Override
            protected void onContentsChanged(int slot, ItemStack previousContents) {
                onChange.run();
            }
        };
    }

    public EnergyHandler energyStorage(int capacity, int maxReceive, int maxExtract) {
        return energyStorage(capacity, maxReceive, maxExtract, () -> {});
    }

    public EnergyHandler energyStorage(int capacity, int maxReceive, int maxExtract, Runnable onChange) {
        requireNonNegative("capacity", capacity);
        requireNonNegative("maxReceive", maxReceive);
        requireNonNegative("maxExtract", maxExtract);
        Objects.requireNonNull(onChange);
        return new SimpleEnergyHandler(capacity, maxReceive, maxExtract) {
            @Override
            protected void onEnergyChanged(int previousAmount) {
                onChange.run();
            }
        };
    }

    public ResourceHandler<FluidResource> fluidTank(int capacity) {
        return fluidTank(capacity, () -> {});
    }

    public ResourceHandler<FluidResource> fluidTank(int capacity, Runnable onChange) {
        requireNonNegative("capacity", capacity);
        Objects.requireNonNull(onChange);
        return new FluidStacksResourceHandler(1, capacity) {
            @Override
            protected void onContentsChanged(int slot, FluidStack previousContents) {
                onChange.run();
            }
        };
    }

    private static void requireNonNegative(String name, int value) {
        if (value < 0) {
            throw new IllegalArgumentException("[NEKO-4012] Capability " + name
                    + " must be non-negative: " + value);
        }
    }
}
//?}
