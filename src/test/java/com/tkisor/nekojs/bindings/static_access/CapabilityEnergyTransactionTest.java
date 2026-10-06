//? if neoforge && >=26 {
package com.tkisor.nekojs.bindings.static_access;

import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CapabilityEnergyTransactionTest {
    @Test
    void factoryStorageRollsBackWhenTheRootAborts() {
        EnergyHandler storage = new CapabilitiesJS().energyStorage(100, 30, 20);
        try (Transaction transaction = Transaction.openRoot()) {
            assertEquals(30, storage.insert(90, transaction));
            assertEquals(30, storage.getAmountAsLong());
        }
        assertEquals(0, storage.getAmountAsLong());
    }

    @Test
    void committedChildStillRollsBackWithItsParent() {
        EnergyHandler storage = new CapabilitiesJS().energyStorage(100, 100, 100);
        try (Transaction outer = Transaction.openRoot()) {
            storage.insert(10, outer);
            try (Transaction nested = Transaction.open(outer)) {
                storage.insert(25, nested);
                nested.commit();
            }
            assertEquals(35, storage.getAmountAsLong());
        }
        assertEquals(0, storage.getAmountAsLong());
    }

    @Test
    void commitAndTransferLimitsUseTheNativeContract() {
        EnergyHandler storage = new CapabilitiesJS().energyStorage(100, 30, 20);
        try (Transaction transaction = Transaction.openRoot()) {
            assertEquals(30, storage.insert(90, transaction));
            transaction.commit();
        }
        assertEquals(30, storage.getAmountAsLong());
        try (Transaction transaction = Transaction.openRoot()) {
            assertEquals(20, storage.extract(90, transaction));
            transaction.commit();
        }
        assertEquals(10, storage.getAmountAsLong());
    }

    @Test
    void ownerNotificationRunsOnlyAfterCommit() {
        java.util.concurrent.atomic.AtomicInteger changes = new java.util.concurrent.atomic.AtomicInteger();
        EnergyHandler storage = new CapabilitiesJS().energyStorage(100, 100, 100, changes::incrementAndGet);
        try (Transaction transaction = Transaction.openRoot()) {
            storage.insert(25, transaction);
            assertEquals(0, changes.get());
        }
        assertEquals(0, changes.get());
        try (Transaction transaction = Transaction.openRoot()) {
            storage.insert(25, transaction);
            transaction.commit();
        }
        assertEquals(1, changes.get());
    }

    @Test
    void negativeConfigurationIsRejectedWithoutConstructingPlatformResources() {
        CapabilitiesJS factories = new CapabilitiesJS();
        assertThrows(IllegalArgumentException.class, () -> factories.itemHandler(-1));
        assertThrows(IllegalArgumentException.class, () -> factories.fluidTank(-1));
        assertThrows(IllegalArgumentException.class, () -> factories.energyStorage(100, -1, 20));
        assertThrows(IllegalArgumentException.class, () -> factories.energyStorage(100, 30, -1));
    }
}
//?}
