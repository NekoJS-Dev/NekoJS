//~ mc_legacy_api
package com.tkisor.nekojs.js.type_adapter;

import com.mojang.serialization.Lifecycle;
import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.catalog.RegistryTypeCatalogEntry;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RegistryTypeCatalogsTest {
    @Test
    void publishedAdapterSuppliesLiveValuesAndTagsWithoutRetainingOldSnapshots() {
        var registry = registry("fixture:live");
        Registry.register(registry, Identifier.parse("fixture:first"), new Object());
        List<JSTypeAdapter<?>> adapters = List.of(new SimpleRegistryBasedAdapter<>(registry, Object.class, "Fixture"));
        var first = RegistryTypeCatalogs.snapshot(Map.of(), adapters, source -> {
            assertSame(registry, source);
            return List.of("fixture:z", "fixture:a", "fixture:a");
        });
        assertEquals(List.of(new RegistryTypeCatalogEntry("Fixture", List.of("fixture:first"),
                List.of("fixture:a", "fixture:z"))), first);
        Registry.register(registry, Identifier.parse("fixture:second"), new Object());
        var second = RegistryTypeCatalogs.snapshot(Map.of(), adapters, source -> List.of());
        assertEquals(List.of("fixture:first", "fixture:second"), second.getFirst().entries());
        assertEquals(List.of("fixture:first"), first.getFirst().entries());
    }

    @Test
    void platformSourceAndAdapterWithTheSameRegistryAreCollectedOnce() {
        var registry = registry("fixture:same");
        var adapter = new SimpleRegistryBasedAdapter<>(registry, Object.class, "Fixture");
        var entries = RegistryTypeCatalogs.snapshot(Map.of("Fixture", registry), List.of(adapter), source -> List.of());
        assertEquals(List.of(new RegistryTypeCatalogEntry("Fixture", List.of(), List.of())), entries);
    }

    @Test
    void equalContentsDoNotHideDifferentRegistrySources() {
        var first = registry("fixture:first");
        var second = registry("fixture:second");
        var failure = assertThrows(IllegalStateException.class, () -> RegistryTypeCatalogs.snapshot(
                Map.of("Fixture", first), List.of(new SimpleRegistryBasedAdapter<>(second, Object.class, "Fixture")),
                source -> List.of()));
        assertTrue(failure.getMessage().contains("NEKO-4052"), failure.getMessage());
        assertTrue(failure.getMessage().contains("fixture:first"), failure.getMessage());
        assertTrue(failure.getMessage().contains("fixture:second"), failure.getMessage());
    }

    @Test
    void adapterOrderingDoesNotChangeCatalogOrdering() {
        var registry = registry("fixture:order");
        var first = new SimpleRegistryBasedAdapter<>(registry, Object.class, "Alpha");
        var last = new SimpleRegistryBasedAdapter<>(registry, Object.class, "Zulu");
        Map<String, Registry<?>> baseline = new LinkedHashMap<>();
        baseline.put("Platform", registry);
        var expected = RegistryTypeCatalogs.snapshot(baseline, List.of(first, last), source -> List.of());
        assertEquals(expected, RegistryTypeCatalogs.snapshot(baseline, List.of(last, first), source -> List.of()));
        assertEquals(List.of("Platform", "Alpha", "Zulu"), expected.stream()
                .map(RegistryTypeCatalogEntry::typeName).toList());
    }

    @Test
    void existingProviderMetadataIsPreservedWhileMissingAdapterAliasesAreAppended() {
        var registry = registry("fixture:append");
        Registry.register(registry, Identifier.parse("fixture:live"), new Object());
        var explicit = new RegistryTypeCatalogEntry("Explicit", List.of("fixture:owned"), List.of("fixture:tag"));
        var entries = RegistryTypeCatalogs.append(List.of(explicit), Map.of("UnusedPlatform", registry), List.of(
                new SimpleRegistryBasedAdapter<>(registry, Object.class, "Explicit"),
                new SimpleRegistryBasedAdapter<>(registry, Object.class, "Added")), source -> List.of());
        assertEquals(List.of(explicit, new RegistryTypeCatalogEntry("Added", List.of("fixture:live"), List.of())), entries);
    }

    @Test
    void explicitMetadataStillRejectsAliasAndGeneratedSymbolConflicts() {
        var first = new RegistryTypeCatalogEntry("Fixture", List.of("fixture:first"), List.of());
        var second = new RegistryTypeCatalogEntry("Fixture", List.of("fixture:second"), List.of());
        assertThrows(IllegalStateException.class, () -> RegistryTypeCatalogs.append(
                List.of(first, second), Map.of(), List.of(), source -> List.of()));
        var registry = registry("fixture:append_symbols");
        assertThrows(IllegalStateException.class, () -> RegistryTypeCatalogs.append(List.of(first), Map.of(),
                List.of(new SimpleRegistryBasedAdapter<>(registry, Object.class, "FixtureTag")), source -> List.of()));
    }

    @Test
    void generatedTagAndNamespaceSymbolsCannotCollideWithRegistryNames() {
        var registry = registry("fixture:symbols");
        assertThrows(IllegalStateException.class, () -> RegistryTypeCatalogs.snapshot(
                Map.of("Namespace", registry), List.of(), source -> List.of()));
        assertThrows(IllegalStateException.class, () -> RegistryTypeCatalogs.snapshot(
                Map.of("Fixture", registry, "FixtureTag", registry), List.of(), source -> List.of()));
    }

    private static MappedRegistry<Object> registry(String name) {
        ResourceKey<Registry<Object>> key = ResourceKey.createRegistryKey(Identifier.parse(name));
        return new MappedRegistry<>(key, Lifecycle.stable(), false);
    }
}
