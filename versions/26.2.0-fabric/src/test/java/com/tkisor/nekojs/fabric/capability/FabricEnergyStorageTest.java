//? if fabric {
package com.tkisor.nekojs.fabric.capability;

import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FabricEnergyStorageTest {
    @Test
    void abortRestoresContentsAndDoesNotNotifyTheOwner() {
        AtomicInteger changes = new AtomicInteger();
        FabricEnergyStorage storage = new FabricEnergyStorage(100, 30, 20, changes::incrementAndGet);
        try (Transaction transaction = Transaction.openOuter()) {
            assertEquals(30, storage.insert(90, transaction));
            assertEquals(30, storage.getAmount());
        }
        assertEquals(0, storage.getAmount());
        assertEquals(0, changes.get());
    }

    @Test
    void nestedCommitIsStillRevertedByOuterAbort() {
        FabricEnergyStorage storage = new FabricEnergyStorage(100, 100, 100, () -> {});
        try (Transaction outer = Transaction.openOuter()) {
            storage.insert(10, outer);
            try (Transaction nested = outer.openNested()) {
                storage.insert(25, nested);
                nested.commit();
            }
            assertEquals(35, storage.getAmount());
        }
        assertEquals(0, storage.getAmount());
    }

    @Test
    void commitKeepsChangesAndExtractionRespectsItsLimit() {
        AtomicInteger changes = new AtomicInteger();
        FabricEnergyStorage storage = new FabricEnergyStorage(100, 30, 20, changes::incrementAndGet);
        try (Transaction transaction = Transaction.openOuter()) {
            assertEquals(30, storage.insert(90, transaction));
            transaction.commit();
        }
        assertEquals(30, storage.getAmount());
        assertEquals(1, changes.get());
        try (Transaction transaction = Transaction.openOuter()) {
            assertEquals(20, storage.extract(90, transaction));
            transaction.commit();
        }
        assertEquals(10, storage.getAmount());
        assertEquals(2, changes.get());
    }

    @Test
    void negativeAmountsAreRejectedBeforeMutation() {
        FabricEnergyStorage storage = new FabricEnergyStorage(100, 30, 20, () -> {});
        try (Transaction transaction = Transaction.openOuter()) {
            assertThrows(IllegalArgumentException.class, () -> storage.insert(-1, transaction));
            assertThrows(IllegalArgumentException.class, () -> storage.extract(-1, transaction));
        }
        assertEquals(0, storage.getAmount());
        assertThrows(IllegalArgumentException.class, () -> new FabricEnergyStorage(-1, 1, 1, () -> {}));
    }
}
//?}
