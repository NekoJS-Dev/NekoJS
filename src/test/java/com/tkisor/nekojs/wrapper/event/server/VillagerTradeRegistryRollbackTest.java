//? if >=26 {
package com.tkisor.nekojs.wrapper.event.server;

import com.mojang.serialization.Lifecycle;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VillagerTradeRegistryRollbackTest {
    @Test
    void rollbackRestoresLookupIdentityDenseIdsAndRegistrationMetadata() throws Exception {
        ResourceKey<Registry<Object>> key = ResourceKey.createRegistryKey(Identifier.parse("test:trade_rollback"));
        var registry = new MappedRegistry<>(key, Lifecycle.stable());
        var acceptedKey = ResourceKey.create(key, Identifier.parse("test:accepted"));
        Object accepted = new Object();
        Registry.register(registry, acceptedKey, accepted);
        var acceptedHolder = registry.get(acceptedKey).orElseThrow();
        var tagsField = acceptedHolder.getClass().getDeclaredField("tags");
        tagsField.setAccessible(true);
        var tags = java.util.Set.of(net.minecraft.tags.TagKey.create(key, Identifier.parse("test:accepted_tag")));
        registry.freeze();
        tagsField.set(acceptedHolder, tags);
        var info = registry.registrationInfo(acceptedKey);
        var checkpointClass = Class.forName(VillagerTradeDomainOwner.class.getName() + "$RegistryCheckpoint");
        var constructor = checkpointClass.getDeclaredConstructor(MappedRegistry.class);
        constructor.setAccessible(true);
        Object checkpoint = constructor.newInstance(registry);
        var remove = VillagerTradeDomainOwner.class.getDeclaredMethod("unregister", MappedRegistry.class, ResourceKey.class);
        remove.setAccessible(true);
        assertTrue((boolean) remove.invoke(null, registry, acceptedKey));
        var rejectedKey = ResourceKey.create(key, Identifier.parse("test:rejected"));
        Object rejected = new Object();
        var unfreeze = VillagerTradeDomainOwner.class.getDeclaredMethod("withUnfrozen", MappedRegistry.class, Runnable.class);
        unfreeze.setAccessible(true);
        unfreeze.invoke(null, registry, (Runnable) () -> Registry.register(registry, rejectedKey, rejected));
        assertFalse(registry.containsKey(acceptedKey.identifier()));
        var restore = checkpointClass.getDeclaredMethod("restoreAfterFailure", Throwable.class);
        restore.setAccessible(true);
        var failure = new IllegalStateException("injected publication failure");
        assertTrue((boolean) restore.invoke(checkpoint, failure));
        assertEquals(0, failure.getSuppressed().length);
        assertSame(accepted, registry.getValue(acceptedKey));
        assertSame(acceptedHolder, registry.get(acceptedKey).orElseThrow());
        assertEquals(tags, tagsField.get(acceptedHolder));
        assertEquals(0, registry.getId(accepted));
        assertSame(accepted, registry.byId(0));
        assertEquals(info, registry.registrationInfo(acceptedKey));
        assertFalse(registry.containsKey(rejectedKey.identifier()));
        assertEquals(-1, registry.getId(rejected));
        assertEquals(1, registry.size());
    }
}
//?}
