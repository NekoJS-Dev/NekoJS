package com.tkisor.nekojs.fabric.client;

import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.MissingItemModel;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.resources.model.cuboid.ItemTransforms;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FabricSpawnEggModelTest {
    private static final Identifier EGG = Identifier.parse("nekojs:nested/mob_spawn_egg");
    private static final ItemModel VISIBLE = (state, stack, resolver, context, level, owner, seed) -> {};

    private static MissingItemModel missing() {
        return new MissingItemModel(List.of(),
                new ModelRenderProperties(false, null, ItemTransforms.NO_TRANSFORMS));
    }

    @Test
    void missingRegisteredEggResolvesToTheManagersVanillaEggModel() {
        try (var resources = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of())) {
            Set<Identifier> fallback = FabricSpawnEggModels.missingDefinitions(Set.of(EGG), resources);
            AtomicReference<Identifier> requested = new AtomicReference<>();
            assertSame(VISIBLE, FabricSpawnEggModels.resolve(EGG, missing(), fallback, id -> {
                requested.set(id);
                return VISIBLE;
            }));
            assertEquals(Identifier.parse("minecraft:egg"), requested.get());
            assertThrows(UnsupportedOperationException.class, () -> fallback.add(Identifier.parse("demo:other")));
        }
    }

    @Test
    void unregisteredMissingItemsAreNeverReplaced() {
        MissingItemModel loaded = missing();
        assertSame(loaded, FabricSpawnEggModels.resolve(Identifier.parse("demo:unknown"), loaded,
                Set.of(EGG), id -> { throw new AssertionError("Unregistered item must not request a fallback"); }));
    }

    @Test
    void vanillaEggIsAlwaysTheRecursionEndpoint() {
        MissingItemModel original = missing();
        Identifier vanilla = Identifier.parse("minecraft:egg");
        assertSame(original, FabricSpawnEggModels.resolve(vanilla, original, Set.of(vanilla),
                id -> { throw new AssertionError("Fallback endpoint must not recurse"); }));
    }

    @Test
    void nativeLookupFailureIsNotHiddenByTheFallback() {
        IllegalStateException expected = new IllegalStateException("native lookup fixture");
        assertSame(expected, assertThrows(IllegalStateException.class,
                () -> FabricSpawnEggModels.resolve(EGG, missing(), Set.of(EGG), id -> { throw expected; })));
    }

    @Test
    void anotherPluginsVisibleModelIsPreservedEvenWithoutAnItemDefinition() {
        assertSame(VISIBLE, FabricSpawnEggModels.resolve(EGG, VISIBLE, Set.of(EGG),
                id -> { throw new AssertionError("A visible model must not be replaced"); }));
    }

    @Test
    void customResourceDefinitionHasPriorityEvenWhenItWouldBakeToAMissingModel() {
        Identifier definition = Identifier.parse("nekojs:items/nested/mob_spawn_egg.json");
        try (var resources = new MultiPackResourceManager(PackType.CLIENT_RESOURCES,
                List.of(new MemoryPack("custom", Map.of(definition, "invalid-custom-json"))))) {
            Set<Identifier> fallback = FabricSpawnEggModels.missingDefinitions(Set.of(EGG), resources);
            assertEquals(Set.of(), fallback);
            MissingItemModel loaded = missing();
            assertSame(loaded, FabricSpawnEggModels.resolve(EGG, loaded, fallback,
                    id -> { throw new AssertionError("User definition must not be hidden by a fallback"); }));
        }
    }

    @Test
    void reloadingAfterAddingAndRemovingAnOverrideRecomputesTheFallbackSet() {
        Identifier definition = FabricSpawnEggModels.itemDefinition(EGG);
        try (var absent = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of());
             var custom = new MultiPackResourceManager(PackType.CLIENT_RESOURCES,
                     List.of(new MemoryPack("custom", Map.of(definition, "{}"))))) {
            assertEquals(Set.of(EGG), FabricSpawnEggModels.missingDefinitions(Set.of(EGG), absent));
            assertEquals(Set.of(), FabricSpawnEggModels.missingDefinitions(Set.of(EGG), custom));
            assertEquals(Set.of(EGG), FabricSpawnEggModels.missingDefinitions(Set.of(EGG), absent));
        }
    }

    @Test
    void selectedNativeResourcePackPriorityIsPreserved() throws Exception {
        Identifier definition = FabricSpawnEggModels.itemDefinition(EGG);
        try (var resources = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(
                new MemoryPack("lower", Map.of(definition, "lower")),
                new MemoryPack("user", Map.of(definition, "user"))))) {
            var selected = resources.getResource(definition).orElseThrow();
            assertEquals("user", selected.sourcePackId());
            try (InputStream contents = selected.open()) {
                assertEquals("user", new String(contents.readAllBytes(), StandardCharsets.UTF_8));
            }
            assertEquals(Set.of(), FabricSpawnEggModels.missingDefinitions(Set.of(EGG), resources));
        }
    }

    private record MemoryPack(String name, Map<Identifier, String> resources) implements PackResources {
        @Override
        public IoSupplier<InputStream> getRootResource(String... path) {
            return null;
        }

        @Override
        public IoSupplier<InputStream> getResource(PackType type, Identifier id) {
            String contents = type == PackType.CLIENT_RESOURCES ? resources.get(id) : null;
            return contents == null ? null : () -> new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
            resources.keySet().stream()
                    .filter(id -> id.getNamespace().equals(namespace) && id.getPath().startsWith(path))
                    .forEach(id -> output.accept(id, getResource(type, id)));
        }

        @Override
        public Set<String> getNamespaces(PackType type) {
            return type == PackType.CLIENT_RESOURCES
                    ? resources.keySet().stream().map(Identifier::getNamespace).collect(Collectors.toSet())
                    : Set.of();
        }

        @Override
        public <Metadata> Metadata getMetadataSection(MetadataSectionType<Metadata> type) {
            return null;
        }

        @Override
        public PackLocationInfo location() {
            return new PackLocationInfo(name, Component.literal(name), PackSource.DEFAULT, Optional.empty());
        }

        @Override
        public void close() {}
    }
}
