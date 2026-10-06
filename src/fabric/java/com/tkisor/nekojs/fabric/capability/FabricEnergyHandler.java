package com.tkisor.nekojs.fabric.capability;

import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;

/** Energy lookup contract for Fabric, using the native transfer transaction lifecycle. */
public interface FabricEnergyHandler {
    long getAmount();

    long getCapacity();

    long insert(long maxAmount, TransactionContext transaction);

    long extract(long maxAmount, TransactionContext transaction);
}
