package com.tkisor.nekojs.core.api.facade;

import com.tkisor.nekojs.api.registry.RegistryQueryService;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers the iterable form of {@code RegistryView}, matching KJS {@code RegistryWrapper} and its
 * {@code for (x of Registry.of(...))}: the default {@code iterator()} delegates to {@code all()}.
 * Guest {@code for...of} after forwarding through the engine proxy {@code ApiFacadeProxy} is
 * covered by {@code ApiFacadeProxyTest#iterableImplementationIsForOfAble}.
 */
class RegistryViewIterableTest {

    private static final RegistryQueryService STUB = new RegistryQueryService() {
        @Override
        public boolean hasRegistry(String registryId) {
            return true;
        }

        @Override
        public List<String> all(String registryId) {
            return List.of("minecraft:stone", "minecraft:dirt", "minecraft:sand");
        }

        @Override
        public boolean has(String registryId, String id) {
            return true;
        }

        @Override
        public List<String> tag(String registryId, String tagId) {
            return List.of();
        }
    };

    @Test
    void defaultIteratorWalksAllEntryIds() {
        var view = new DefaultRegistryView(STUB, "minecraft:item");
        var collected = new java.util.ArrayList<String>();
        for (String id : view) collected.add(id);
        assertEquals(List.of("minecraft:stone", "minecraft:dirt", "minecraft:sand"), collected);
    }
}
