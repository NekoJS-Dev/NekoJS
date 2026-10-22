//~ mc_legacy_api
//? if neoforge {
package com.tkisor.nekojs.js.type_adapter;

import com.mojang.serialization.Lifecycle;
import com.tkisor.nekojs.api.NeoForgeCatalogPlatformProvider;
import com.tkisor.nekojs.api.catalog.RegistryTypeCatalogEntry;
import net.minecraft.core.MappedRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NeoForgeCatalogOverrideTest {
    @Test
    void adapterAwareCollectionStillCallsAnExistingNoArgumentOverride() {
        var expected = new RuntimeException("owned provider override");
        var provider = new NeoForgeCatalogPlatformProvider() {
            @Override public Collection<RegistryTypeCatalogEntry> registryTypes() { throw expected; }
        };
        var registry = new MappedRegistry<Object>(ResourceKey.createRegistryKey(
                Identifier.parse("fixture:provider_override")), Lifecycle.stable(), false);
        assertSame(expected, assertThrows(RuntimeException.class, () -> provider.registryTypes(
                List.of(new SimpleRegistryBasedAdapter<>(registry, Object.class, "Fixture")))));
    }
}
//?}
