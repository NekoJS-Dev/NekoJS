package com.tkisor.nekojs.core.api.facade;

import com.tkisor.nekojs.api.registry.RegistryQueryService;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code RegistryView} 的可迭代形态（KJS {@code RegistryWrapper} 的
 * {@code for (x of Registry.of(...))} 对标）：默认 {@code iterator()} 委托 {@code all()}。
 * 经引擎代理 {@code ApiFacadeProxy} 转发后的 guest {@code for...of} 行为在
 * {@code ApiFacadeProxyTest#iterableImplementationIsForOfAble} 覆盖。
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
