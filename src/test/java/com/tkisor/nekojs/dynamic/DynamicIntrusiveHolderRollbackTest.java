//? if >=26 {
package com.tkisor.nekojs.dynamic;

import com.mojang.serialization.Lifecycle;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DynamicIntrusiveHolderRollbackTest {
    @Test
    void repeatedFailedRegistrationDoesNotPoisonTheNextFreeze() {
        var registry = registry(true);
        // Set the runtime guard without binding tags; validate holders through the first real freeze below.
        RegistrySurgery.setFrozen(registry, true);
        for (int attempt = 0; attempt < 2; attempt++) {
            var failure = new IllegalStateException("owned physical registration failure");
            assertSame(failure, assertThrows(IllegalStateException.class, () ->
                    RegistrySurgery.withUnfrozenAndHolders(registry, () -> {
                        registry.createIntrusiveHolder(new Object());
                        throw failure;
                    })));
            assertTrue(RegistrySurgery.isFrozen(registry));
        }
        assertDoesNotThrow(() -> RegistrySurgery.withUnfrozenRegistry(registry, registry::freeze));
    }

    @Test
    void failedAttemptKeepsPreexistingPendingIdentityEvenWhenNewValueIsEqual() {
        var registry = registry(true);
        Object pending = new String("same value");
        var holder = registry.createIntrusiveHolder(pending);
        assertThrows(AssertionError.class, () -> RegistrySurgery.withUnfrozenAndHolders(registry, () -> {
            registry.createIntrusiveHolder(new String("same value"));
            throw new AssertionError("owned construction failure");
        }));
        assertFalse(RegistrySurgery.isFrozen(registry));
        assertSame(holder, registry.createIntrusiveHolder(pending));
        Registry.register(registry, Identifier.parse("test:pending"), pending);
        assertSame(holder, registry.get(Identifier.parse("test:pending")).orElseThrow());
        assertDoesNotThrow(() -> RegistrySurgery.withUnfrozenRegistry(registry, registry::freeze));
    }

    @Test
    void failureDoesNotResurrectAPendingHolderConsumedBySuccessfulRegistration() {
        var registry = registry(true);
        Object accepted = new Object();
        var holder = registry.createIntrusiveHolder(accepted);
        assertThrows(IllegalStateException.class, () -> RegistrySurgery.withUnfrozenAndHolders(registry, () -> {
            Registry.register(registry, Identifier.parse("test:accepted"), accepted);
            registry.createIntrusiveHolder(new Object());
            throw new IllegalStateException("owned later registration failure");
        }));
        assertSame(holder, registry.get(Identifier.parse("test:accepted")).orElseThrow());
        assertSame(accepted, registry.getValue(Identifier.parse("test:accepted")));
        assertDoesNotThrow(() -> RegistrySurgery.withUnfrozenRegistry(registry, registry::freeze));
    }

    @Test
    void temporaryHolderMapIsReleasedAndSubsequentRegistrationStillSucceeds() {
        var registry = registry(false);
        RegistrySurgery.setFrozen(registry, true);
        assertThrows(IllegalStateException.class, () -> RegistrySurgery.withUnfrozenAndHolders(registry, () -> {
            registry.createIntrusiveHolder(new Object());
            throw new IllegalStateException("owned temporary registration failure");
        }));
        assertThrows(IllegalStateException.class, () -> RegistrySurgery.withUnfrozenRegistry(registry,
                () -> registry.createIntrusiveHolder(new Object())));
        Object accepted = new Object();
        assertSame(accepted, RegistrySurgery.withUnfrozenAndHolders(registry, () -> {
            registry.createIntrusiveHolder(accepted);
            return Registry.register(registry, Identifier.parse("test:accepted"), accepted);
        }));
        assertTrue(RegistrySurgery.isFrozen(registry));
        assertSame(accepted, registry.getValue(Identifier.parse("test:accepted")));
        assertDoesNotThrow(() -> RegistrySurgery.withUnfrozenRegistry(registry, registry::freeze));
    }

    private static MappedRegistry<Object> registry(boolean intrusive) {
        ResourceKey<Registry<Object>> key = ResourceKey.createRegistryKey(Identifier.parse("test:intrusive_rollback"));
        return new MappedRegistry<>(key, Lifecycle.stable(), intrusive);
    }
}
//?}
