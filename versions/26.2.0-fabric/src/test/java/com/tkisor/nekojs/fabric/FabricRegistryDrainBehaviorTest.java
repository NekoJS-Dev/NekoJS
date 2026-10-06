package com.tkisor.nekojs.fabric;

import com.tkisor.nekojs.wrapper.registry.gen.RegistryObjectBuilder;
import com.tkisor.nekojs.wrapper.registry.gen.StartupRegistryRuntime;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabricRegistryDrainBehaviorTest {
    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("issue4_test", path);
    }

    private static Supplier<Object> lazyNativeObject() {
        return () -> {
            throw new AssertionError("The recording sink must not construct a native registry object");
        };
    }

    private static final class Batch {
        final StartupRegistryRuntime runtime = new StartupRegistryRuntime("fabric-drain-test");
        final List<ResourceKey<? extends Registry<?>>> passes = new ArrayList<>();
        final List<StartupRegistryRuntime.RegistrationRecord> delivered = new ArrayList<>();
        final List<StartupRegistryRuntime.ErrorRecord> errors = new ArrayList<>();
        final List<Supplier<?>> itemSuppliers = new ArrayList<>();

        void add(ResourceKey<? extends Registry<?>> registry, String path, boolean producesItem) {
            runtime.repository().add(registry, new FixtureBuilder(id(path), producesItem), "fixture", "test");
        }

        void drain() {
            FabricRegistryAdapter.drainPendingRegistries(runtime, registry -> {
                passes.add(registry);
                StartupRegistryRuntime.DrainResult result = runtime.drainFor(registry, (target, definition, supplier) -> {
                    if (target.equals(Registries.ITEM)) {
                        itemSuppliers.add(supplier);
                    } else if (!target.equals(Registries.BLOCK)) {
                        assertEquals("built:" + definition, supplier.get());
                    }
                });
                delivered.addAll(result.registered());
                errors.addAll(result.errors());
            });
        }

        List<Identifier> itemIds() {
            return delivered.stream().filter(record -> record.registry().equals(Registries.ITEM))
                    .map(StartupRegistryRuntime.RegistrationRecord::definition).toList();
        }
    }

    public static final class FixtureBuilder extends RegistryObjectBuilder<Object> {
        private final boolean producesItem;

        FixtureBuilder(Identifier id, boolean producesItem) {
            super(id);
            this.producesItem = producesItem;
        }

        @Override
        public Object build() {
            return "built:" + id;
        }

        @Override
        public void handleAdditionalObjects(AdditionalObjectRegistry registry) {
            if (producesItem) {
                registry.additional(Registries.ITEM, id(id.getPath() + "_item"), lazyNativeObject());
            }
        }
    }

    @Test
    void entityOnlyBatchRefreshesPendingKeysToDeliverItsSpawnEggItem() {
        Batch batch = new Batch();
        batch.add(Registries.ENTITY_TYPE, "entity", true);
        batch.drain();
        assertEquals(List.of(Registries.ENTITY_TYPE, Registries.ITEM), batch.passes);
        assertEquals(List.of(id("entity_item")), batch.itemIds());
        assertEquals(1, batch.itemSuppliers.size());
        assertTrue(batch.errors.isEmpty());
        assertTrue(batch.runtime.isFullyDrained());
    }

    @Test
    void initiallyDeclaredItemDoesNotPassBeforeItsEntityProducer() {
        Batch batch = new Batch();
        batch.add(Registries.ITEM, "declared_item", false);
        batch.add(Registries.ENTITY_TYPE, "entity", true);
        batch.drain();
        assertEquals(List.of(Registries.ENTITY_TYPE, Registries.ITEM), batch.passes);
        assertEquals(List.of(id("declared_item"), id("entity_item")), batch.itemIds());
        assertEquals(2, batch.itemSuppliers.size());
        assertTrue(batch.errors.isEmpty());
        assertTrue(batch.runtime.isFullyDrained());
    }

    @Test
    void initiallyDeclaredItemDoesNotPassBeforeItsBlockProducer() {
        Batch batch = new Batch();
        batch.add(Registries.ITEM, "declared_item", false);
        batch.add(Registries.BLOCK, "block", true);
        batch.drain();
        assertEquals(List.of(Registries.BLOCK, Registries.ITEM), batch.passes);
        assertEquals(List.of(id("declared_item"), id("block_item")), batch.itemIds());
        assertTrue(batch.errors.isEmpty());
        assertTrue(batch.runtime.isFullyDrained());
    }

    @Test
    void allProducersRunBeforeTheSingleItemPassRegardlessOfDeclarationOrder() {
        Batch batch = new Batch();
        batch.add(Registries.ITEM, "declared_item", false);
        batch.add(Registries.ENTITY_TYPE, "entity", true);
        batch.add(Registries.BLOCK, "block", true);
        batch.drain();
        assertEquals(List.of(Registries.BLOCK, Registries.ENTITY_TYPE, Registries.ITEM), batch.passes);
        assertEquals(List.of(id("declared_item"), id("block_item"), id("entity_item")), batch.itemIds());
        assertTrue(batch.errors.isEmpty());
        assertTrue(batch.runtime.isFullyDrained());
    }

    @Test
    void emptyBatchDoesNotInvokeAnyRegistryPass() {
        Batch batch = new Batch();
        batch.drain();
        assertTrue(batch.passes.isEmpty());
        assertTrue(batch.delivered.isEmpty());
        assertTrue(batch.runtime.isFullyDrained());
    }

    @Test
    void unknownRegistrySinkFailureIsConsumedOnceAndDoesNotLoop() {
        StartupRegistryRuntime runtime = new StartupRegistryRuntime("fabric-drain-test");
        ResourceKey<Registry<Object>> unknown = ResourceKey.createRegistryKey(id("unknown_registry"));
        runtime.repository().add(unknown, new FixtureBuilder(id("unknown_entry"), false));
        List<StartupRegistryRuntime.ErrorRecord> errors = new ArrayList<>();
        AtomicInteger passes = new AtomicInteger();
        FabricRegistryAdapter.drainPendingRegistries(runtime, registry -> {
            passes.incrementAndGet();
            errors.addAll(runtime.drainFor(registry, (target, definition, supplier) -> {
                assertEquals("built:" + definition, supplier.get());
                throw new IllegalStateException("unknown fixture registry");
            }).errors());
        });
        assertEquals(1, passes.get());
        assertEquals(1, errors.size());
        assertEquals("platform-register", errors.get(0).source());
        assertEquals(unknown, errors.get(0).registry());
        assertTrue(runtime.isFullyDrained());
    }

    @Test
    void nonConsumingPassFailsWithoutRepeatedlyInvokingItsSink() {
        Batch batch = new Batch();
        batch.add(Registries.ENTITY_TYPE, "entity", true);
        AtomicInteger passes = new AtomicInteger();
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> FabricRegistryAdapter.drainPendingRegistries(batch.runtime, registry -> passes.incrementAndGet()));
        assertTrue(failure.getMessage().contains("NEKO-4028"));
        assertEquals(1, passes.get());
        assertFalse(batch.runtime.isFullyDrained());
    }

    @Test
    void thrownPassFailurePropagatesWithoutRetriesOrPretendingTheBatchIsEmpty() {
        Batch batch = new Batch();
        batch.add(Registries.ENTITY_TYPE, "entity", true);
        IllegalArgumentException expected = new IllegalArgumentException("sink fixture failure");
        AtomicInteger passes = new AtomicInteger();
        IllegalArgumentException actual = assertThrows(IllegalArgumentException.class,
                () -> FabricRegistryAdapter.drainPendingRegistries(batch.runtime, registry -> {
                    passes.incrementAndGet();
                    throw expected;
                }));
        assertSame(expected, actual);
        assertEquals(1, passes.get());
        assertFalse(batch.runtime.isFullyDrained());
    }

    @Test
    void runtimeStillRejectsAdditionalItemsWhenTheItemPassWasAlreadyConsumed() {
        Batch batch = new Batch();
        batch.add(Registries.ENTITY_TYPE, "entity", true);
        batch.runtime.drainFor(Registries.ITEM, (registry, definition, supplier) -> {
            throw new AssertionError("There are no item entries before the entity producer runs");
        });
        batch.drain();
        assertEquals(List.of(Registries.ENTITY_TYPE), batch.passes);
        assertTrue(batch.itemIds().isEmpty());
        assertEquals(1, batch.errors.size());
        assertEquals("additional-target", batch.errors.get(0).source());
        assertTrue(batch.errors.get(0).message().contains("already passed"));
        assertTrue(batch.runtime.isFullyDrained());
    }
}
