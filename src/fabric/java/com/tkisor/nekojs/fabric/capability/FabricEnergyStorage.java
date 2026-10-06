package com.tkisor.nekojs.fabric.capability;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.Objects;

/** Transactional energy storage with explicit owner-controlled persistence. */
public final class FabricEnergyStorage extends SnapshotParticipant<Long> implements FabricEnergyHandler {
    private final long capacity;
    private final long maxReceive;
    private final long maxExtract;
    private final Runnable onChange;
    private long amount;

    public FabricEnergyStorage(long capacity, long maxReceive, long maxExtract, Runnable onChange) {
        requireNonNegative(capacity);
        requireNonNegative(maxReceive);
        requireNonNegative(maxExtract);
        this.capacity = capacity;
        this.maxReceive = maxReceive;
        this.maxExtract = maxExtract;
        this.onChange = Objects.requireNonNull(onChange);
    }

    @Override
    public long getAmount() {
        return amount;
    }

    @Override
    public long getCapacity() {
        return capacity;
    }

    @Override
    public long insert(long maxAmount, TransactionContext transaction) {
        requireNonNegative(maxAmount);
        Objects.requireNonNull(transaction);
        long inserted = Math.min(Math.min(maxAmount, maxReceive), capacity - amount);
        if (inserted > 0) {
            updateSnapshots(transaction);
            amount += inserted;
        }
        return inserted;
    }

    @Override
    public long extract(long maxAmount, TransactionContext transaction) {
        requireNonNegative(maxAmount);
        Objects.requireNonNull(transaction);
        long extracted = Math.min(Math.min(maxAmount, maxExtract), amount);
        if (extracted > 0) {
            updateSnapshots(transaction);
            amount -= extracted;
        }
        return extracted;
    }

    @Override
    protected Long createSnapshot() {
        return amount;
    }

    @Override
    protected void readSnapshot(Long snapshot) {
        amount = snapshot;
    }

    @Override
    protected void onFinalCommit() {
        onChange.run();
    }

    public void writeValue(ValueOutput output) {
        FabricStoragePersistence.requireCommittedSave();
        output.putLong("energy", amount);
    }

    public void readValue(ValueInput input) {
        FabricStoragePersistence.requireLoadOutsideTransaction();
        long saved = FabricStoragePersistence.readRequired(input, "energy", Codec.LONG);
        if (saved < 0 || saved > capacity) {
            throw new IllegalArgumentException("[NEKO-4024] Saved energy is outside the configured capacity: " + saved);
        }
        amount = saved;
    }

    private static void requireNonNegative(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("[NEKO-4012] Energy amount must be non-negative: " + value);
        }
    }
}
