package com.tkisor.nekojs.fabric.capability;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.base.SingleFluidStorage;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.Objects;

/** A one-variant fluid tank measured in native Fabric droplets. */
public final class FabricFluidStorage extends SingleFluidStorage {
    private final long capacity;
    private final Runnable onChange;

    public FabricFluidStorage(long capacity, Runnable onChange) {
        if (capacity < 0) {
            throw new IllegalArgumentException("[NEKO-4012] Fluid capacity must be non-negative: " + capacity);
        }
        this.capacity = capacity;
        this.onChange = Objects.requireNonNull(onChange);
    }

    @Override
    protected long getCapacity(FluidVariant variant) {
        return capacity;
    }

    @Override
    protected void onFinalCommit() {
        onChange.run();
    }

    @Override
    public void writeValue(ValueOutput output) {
        FabricStoragePersistence.requireCommittedSave();
        super.writeValue(output);
    }

    @Override
    public void readValue(ValueInput input) {
        FabricStoragePersistence.requireLoadOutsideTransaction();
        FluidVariant savedVariant = FabricStoragePersistence.readRequired(input, "variant", FluidVariant.CODEC);
        long savedAmount = FabricStoragePersistence.readRequired(input, "amount", Codec.LONG);
        if (savedAmount < 0 || savedAmount > capacity || savedVariant.isBlank() != (savedAmount == 0)) {
            throw new IllegalArgumentException("[NEKO-4024] Saved fluid amount is outside the configured capacity: " + savedAmount);
        }
        variant = savedVariant;
        amount = savedAmount;
    }
}
