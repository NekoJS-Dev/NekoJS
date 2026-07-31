package com.tkisor.nekojs.core.api.facade;

import com.tkisor.nekojs.api.error.ApiErrorCodes;
import com.tkisor.nekojs.api.error.ApiInvocationException;
import com.tkisor.nekojs.api.facade.RegistryView;
import com.tkisor.nekojs.api.registry.RegistryQueryService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultRegistryFacadeTest {
    private final DefaultRegistryFacade registry = new DefaultRegistryFacade(fixtureService());

    @Test
    void exposesExistsAllHasAndTagThroughTheView() {
        RegistryView items = registry.get("minecraft:item");

        assertTrue(items.exists());
        assertEquals(List.of("minecraft:stone", "minecraft:dirt"), items.all());
        assertTrue(items.has("minecraft:stone"));
        assertFalse(items.has("minecraft:netherite_block"));
        assertEquals(List.of("minecraft:stone"), items.tag("minecraft:planks"));
    }

    @Test
    void reportsMissingRegistriesAsEmptyViews() {
        RegistryView missing = registry.get("minecraft:not_a_registry");

        assertFalse(missing.exists());
        assertEquals(List.of(), missing.all());
        assertFalse(missing.has("minecraft:stone"));
        assertEquals(List.of(), missing.tag("minecraft:planks"));
    }

    @Test
    void rejectsBlankRegistryIds() {
        ApiInvocationException error = assertThrows(ApiInvocationException.class, () -> registry.get("  "));
        assertEquals(ApiErrorCodes.TYPE_MISMATCH, error.code());
    }

    @Test
    void rejectsBlankIdsInViewQueries() {
        RegistryView items = registry.get("minecraft:item");

        assertEquals(ApiErrorCodes.TYPE_MISMATCH,
                assertThrows(ApiInvocationException.class, () -> items.has("")).code());
        assertEquals(ApiErrorCodes.TYPE_MISMATCH,
                assertThrows(ApiInvocationException.class, () -> items.tag(" ")).code());
    }

    private static RegistryQueryService fixtureService() {
        return new RegistryQueryService() {
            @Override
            public boolean hasRegistry(String registryId) {
                return "minecraft:item".equals(registryId);
            }

            @Override
            public List<String> all(String registryId) {
                return "minecraft:item".equals(registryId)
                        ? List.of("minecraft:stone", "minecraft:dirt")
                        : List.of();
            }

            @Override
            public boolean has(String registryId, String id) {
                return "minecraft:item".equals(registryId)
                        && Set.of("minecraft:stone", "minecraft:dirt").contains(id);
            }

            @Override
            public List<String> tag(String registryId, String tagId) {
                return "minecraft:item".equals(registryId)
                        && "minecraft:planks".equals(tagId)
                        ? List.of("minecraft:stone")
                        : List.of();
            }
        };
    }
}
