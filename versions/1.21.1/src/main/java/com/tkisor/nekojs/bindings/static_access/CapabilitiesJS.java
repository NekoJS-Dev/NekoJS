package com.tkisor.nekojs.bindings.static_access;

import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.Objects;

/** Creates legacy NeoForge storage; owner callbacks run only after actual changes, not simulations. */
public class CapabilitiesJS {
    public ItemStackHandler itemHandler(int size) {
        return itemHandler(size, () -> {});
    }

    public ItemStackHandler itemHandler(int size, Runnable onChange) {
        requireNonNegative("size", size);
        Objects.requireNonNull(onChange);
        return new ItemStackHandler(size) {
            @Override
            protected void onContentsChanged(int slot) {
                onChange.run();
            }
        };
    }

    public EnergyStorage energyStorage(int capacity, int maxReceive, int maxExtract) {
        return energyStorage(capacity, maxReceive, maxExtract, () -> {});
    }

    public EnergyStorage energyStorage(int capacity, int maxReceive, int maxExtract, Runnable onChange) {
        requireNonNegative("capacity", capacity);
        requireNonNegative("maxReceive", maxReceive);
        requireNonNegative("maxExtract", maxExtract);
        Objects.requireNonNull(onChange);
        return new EnergyStorage(capacity, maxReceive, maxExtract) {
            @Override
            public int receiveEnergy(int amount, boolean simulate) {
                int received = super.receiveEnergy(amount, simulate);
                if (!simulate && received > 0) {
                    onChange.run();
                }
                return received;
            }

            @Override
            public int extractEnergy(int amount, boolean simulate) {
                int extracted = super.extractEnergy(amount, simulate);
                if (!simulate && extracted > 0) {
                    onChange.run();
                }
                return extracted;
            }
        };
    }

    public FluidTank fluidTank(int capacity) {
        return fluidTank(capacity, () -> {});
    }

    public FluidTank fluidTank(int capacity, Runnable onChange) {
        requireNonNegative("capacity", capacity);
        Objects.requireNonNull(onChange);
        return new FluidTank(capacity) {
            @Override
            protected void onContentsChanged() {
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
