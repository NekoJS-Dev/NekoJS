package com.tkisor.nekojs.fabric.capability;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.tkisor.nekojs.bindings.static_access.CapabilitiesJS;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabricStoragePersistenceTest {
    private static ValueInput input(CompoundTag tag) {
        return TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()), tag);
    }

    private static CompoundTag savedEnergy(long amount) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("energy", amount);
        return tag;
    }

    private static CompoundTag save(FabricEnergyStorage storage) {
        TagValueOutput output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        storage.writeValue(output);
        return output.buildResult();
    }

    @Test
    void committedEnergyRoundTripAndExtractionPreserveAmountsWithoutLoadNotifications() {
        AtomicInteger changes = new AtomicInteger();
        FabricEnergyStorage storage = new CapabilitiesJS().energyStorage(100, 30, 20, changes::incrementAndGet);
        try (Transaction transaction = Transaction.openOuter()) {
            assertEquals(30, storage.insert(90, transaction));
            assertEquals(0, changes.get());
            transaction.commit();
        }
        assertEquals(1, changes.get());
        FabricEnergyStorage restored = new FabricEnergyStorage(100, 30, 20, changes::incrementAndGet);
        restored.readValue(input(save(storage)));
        assertEquals(30, restored.getAmount());
        assertEquals(1, changes.get());
        try (Transaction transaction = Transaction.openOuter()) {
            assertEquals(20, restored.extract(90, transaction));
        }
        assertEquals(30, restored.getAmount());
        assertEquals(1, changes.get());
        try (Transaction transaction = Transaction.openOuter()) {
            assertEquals(20, restored.extract(90, transaction));
            transaction.commit();
        }
        assertEquals(10, save(restored).getLong("energy").orElseThrow());
        assertEquals(2, changes.get());
    }

    @Test
    void missingMalformedAndOutOfBoundsEnergyLoadsRetainPreviousState() {
        AtomicInteger changes = new AtomicInteger();
        FabricEnergyStorage storage = new FabricEnergyStorage(100, 100, 100, changes::incrementAndGet);
        storage.readValue(input(savedEnergy(50)));
        CompoundTag malformed = new CompoundTag();
        malformed.putString("energy", "not-a-number");
        for (CompoundTag bad : new CompoundTag[] { new CompoundTag(), malformed, savedEnergy(-1), savedEnergy(101) }) {
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> storage.readValue(input(bad)));
            assertTrue(failure.getMessage().startsWith("[NEKO-4024]"));
            assertEquals(50, storage.getAmount());
            assertEquals(0, changes.get());
        }
    }

    @Test
    void nativePartialCodecResultsAreRejectedRatherThanAcceptedAsRecoveryValues() {
        Codec<Long> partial = Codec.LONG.flatXmap(
                amount -> DataResult.error(() -> "partial-fixture", amount), DataResult::success);
        ValueInput value = input(savedEnergy(40));
        assertEquals(40L, value.read("energy", partial).orElseThrow());
        assertThrows(IllegalArgumentException.class,
                () -> FabricStoragePersistence.readRequired(value, "energy", partial));
    }

    @Test
    void loadingAndSavingTentativeStateAreRejectedWithoutBreakingNestedRollback() {
        FabricEnergyStorage storage = new FabricEnergyStorage(100, 100, 100, () -> {});
        storage.readValue(input(savedEnergy(10)));
        try (Transaction outer = Transaction.openOuter()) {
            assertThrows(IllegalStateException.class, () -> storage.readValue(input(savedEnergy(80))));
            storage.insert(20, outer);
            try (Transaction nested = outer.openNested()) {
                storage.insert(5, nested);
                assertThrows(IllegalStateException.class, () -> storage.readValue(input(savedEnergy(80))));
                assertThrows(IllegalStateException.class, () -> save(storage));
                nested.commit();
            }
            assertEquals(35, storage.getAmount());
        }
        assertEquals(10, storage.getAmount());
        assertEquals(10, save(storage).getLong("energy").orElseThrow());
    }

    @Test
    void finalCommitOwnerCallbackCanSaveAlreadyCommittedContents() {
        AtomicReference<CompoundTag> persisted = new AtomicReference<>();
        AtomicReference<FabricEnergyStorage> owner = new AtomicReference<>();
        FabricEnergyStorage storage = new FabricEnergyStorage(100, 100, 100,
                () -> persisted.set(save(owner.get())));
        owner.set(storage);
        try (Transaction transaction = Transaction.openOuter()) {
            storage.insert(25, transaction);
            transaction.commit();
        }
        assertEquals(25, persisted.get().getLong("energy").orElseThrow());
    }

    @Test
    void zeroSlotInventoryPersistenceObservesTheSameTransactionBoundary() {
        FabricItemStorage storage = new FabricItemStorage(0, () -> {});
        TagValueOutput output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        storage.writeValue(output);
        storage.readValue(input(output.buildResult()));
        assertEquals(0, storage.getSlotCount());
        try (Transaction transaction = Transaction.openOuter()) {
            assertThrows(IllegalStateException.class, () -> storage.readValue(input(new CompoundTag())));
            assertThrows(IllegalStateException.class, () -> storage.writeValue(output));
        }
    }

    @Test
    void energyLongCapacityDoesNotOverflowOnInsertionAndFactoriesRejectNegativeConfiguration() {
        FabricEnergyStorage storage = new FabricEnergyStorage(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, () -> {});
        storage.readValue(input(savedEnergy(Long.MAX_VALUE - 2)));
        try (Transaction transaction = Transaction.openOuter()) {
            assertEquals(2, storage.insert(Long.MAX_VALUE, transaction));
            transaction.commit();
        }
        assertEquals(Long.MAX_VALUE, storage.getAmount());
        CapabilitiesJS factories = new CapabilitiesJS();
        assertThrows(IllegalArgumentException.class, () -> factories.itemHandler(-1));
        assertThrows(IllegalArgumentException.class, () -> factories.fluidTank(-1));
        assertThrows(IllegalArgumentException.class, () -> factories.energyStorage(100, -1, 1));
    }
}
