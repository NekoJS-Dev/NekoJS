package com.tkisor.nekujs.legacyrecipe;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import dev.latvian.mods.kubejs.registry.BuilderBase;
import dev.latvian.mods.kubejs.registry.BuilderTypeCatalog;
import dev.latvian.mods.kubejs.registry.RegistryObjectStorage;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RegistryAbiTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("recipe_abi_test", path); }
    public static final class TextBuilder extends BuilderBase<String> {
        public TextBuilder(ResourceLocation id) { super(id); }
        @Override public String createObject() { return "created"; }
        @Override public String transformObject(String value) { return value.toUpperCase(java.util.Locale.ROOT); }
    }

    @Test void storageOfIsCanonicalAndCodecUsesActualRegistryKey() {
        ResourceKey<Registry<String>> key = ResourceKey.createRegistryKey(id("chemical"));
        var storage = RegistryObjectStorage.of(key);
        assertSame(storage, RegistryObjectStorage.of(ResourceKey.<String>createRegistryKey(id("chemical"))));
        assertSame(key, storage.key);
        assertEquals(new JsonPrimitive("recipe_abi_test:chemical"), RegistryObjectStorage.CODEC.encodeStart(JsonOps.INSTANCE, storage).getOrThrow());
        assertSame(storage, RegistryObjectStorage.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive("recipe_abi_test:chemical")).getOrThrow());
    }

    @Test void builderObjectsIterateInInsertionOrderWithoutFakeRegistryInstallation() {
        ResourceKey<Registry<String>> key = ResourceKey.createRegistryKey(id("iteration"));
        var storage = RegistryObjectStorage.of(key);
        var first = new TextBuilder(id("first"));
        var second = new TextBuilder(id("second"));
        try {
            storage.objects.put(first.id, first);
            storage.objects.put(second.id, second);
            var iterator = storage.iterator();
            assertSame(first, iterator.next());
            assertSame(second, iterator.next());
            assertFalse(iterator.hasNext());
            assertTrue(assertThrows(IllegalStateException.class, first::get).getMessage().startsWith("NEKO-4237:"));
            assertEquals("CREATED", first.createTransformedObject());
            assertEquals("CREATED", first.get());
            assertEquals(id("prefix_first_suffix"), first.newID("prefix_", "_suffix"));
        } finally {
            storage.objects.clear();
        }
    }

    @Test void builderHookRecordsActualFactoryAndRejectsInstallation() {
        ResourceKey<Registry<String>> key = ResourceKey.createRegistryKey(id("builders"));
        var catalog = new BuilderTypeCatalog();
        catalog.of(key, callback -> {
            callback.addDefault(TextBuilder.class, TextBuilder::new);
            callback.add(id("text"), TextBuilder.class, TextBuilder::new);
        });
        var type = catalog.defaultTypes().get(key);
        assertEquals(ResourceLocation.fromNamespaceAndPath("kubejs", "default"), type.type());
        assertEquals(TextBuilder.class, type.builderClass());
        assertInstanceOf(TextBuilder.class, type.factory().createBuilder(id("actual_factory")));
        assertEquals(TextBuilder.class, catalog.namedTypes().get(key).get(id("text")).builderClass());
        assertThrows(UnsupportedOperationException.class, () -> catalog.defaultTypes().clear());
        assertThrows(UnsupportedOperationException.class, () -> catalog.namedTypes().get(key).clear());
        assertTrue(assertThrows(UnsupportedOperationException.class, catalog::createRegistryObjects).getMessage().startsWith("NEKO-4253:"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> catalog.addDefault(key, TextBuilder.class, TextBuilder::new)).getMessage().startsWith("NEKO-4251:"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> catalog.of(key, callback -> callback.add(id("text"), TextBuilder.class, TextBuilder::new))).getMessage().startsWith("NEKO-4252:"));
    }
}
