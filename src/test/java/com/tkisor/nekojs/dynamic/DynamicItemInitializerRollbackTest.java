//? if >=26 {
package com.tkisor.nekojs.dynamic;

import com.mojang.serialization.Lifecycle;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class DynamicItemInitializerRollbackTest {
    @Test
    void rollbackRemovesOnlyAttemptOwnedInitializersAndAllowsSubsequentComponentRebuild() {
        ResourceKey<Registry<Object>> registryKey = ResourceKey.createRegistryKey(Identifier.parse("test:components"));
        var accepted = ResourceKey.create(registryKey, Identifier.parse("test:accepted"));
        var failed = ResourceKey.create(registryKey, Identifier.parse("test:failed"));
        var foreign = ResourceKey.create(registryKey, Identifier.parse("test:foreign"));
        var registry = new MappedRegistry<>(registryKey, Lifecycle.stable());
        Registry.register(registry, accepted, new Object());
        Registry.register(registry, foreign, new Object());
        registry.freeze();
        var provider = HolderLookup.Provider.create(Stream.of(registry));
        var initializers = new DataComponentInitializers();
        initializers.add(accepted, (builder, context, initializerKey) -> {});
        var checkpoint = RegistrySurgery.checkpointInitializers(initializers);
        initializers.add(failed, (builder, context, initializerKey) -> {});
        initializers.add(foreign, (builder, context, initializerKey) -> {});
        assertThrows(IllegalStateException.class, () -> initializers.build(provider),
                "an orphan initializer prevents the next resource reload from reading the registry");
        RegistrySurgery.rollbackInitializers(initializers, checkpoint, Set.of(failed));
        assertEquals(2, RegistrySurgery.checkpointInitializers(initializers).size());
        assertDoesNotThrow(() -> initializers.build(provider));
        RegistrySurgery.rollbackInitializers(initializers, checkpoint, Set.of(failed));
        assertEquals(2, RegistrySurgery.checkpointInitializers(initializers).size(), "rollback is idempotent");
    }

    @Test
    void rollbackKeepsPreexistingRecordsEvenWhenTheyShareTheAttemptKey() {
        ResourceKey<Registry<Object>> registry = ResourceKey.createRegistryKey(Identifier.parse("test:components"));
        var key = ResourceKey.create(registry, Identifier.parse("test:shared"));
        var initializers = new DataComponentInitializers();
        initializers.add(key, (builder, context, initializerKey) -> {});
        var checkpoint = RegistrySurgery.checkpointInitializers(initializers);
        initializers.add(key, (builder, context, initializerKey) -> {});
        RegistrySurgery.rollbackInitializers(initializers, checkpoint, Set.of(key));
        var after = RegistrySurgery.checkpointInitializers(initializers);
        assertEquals(1, after.size());
        assertSame(checkpoint.getFirst(), after.getFirst());
    }
}
//?}
