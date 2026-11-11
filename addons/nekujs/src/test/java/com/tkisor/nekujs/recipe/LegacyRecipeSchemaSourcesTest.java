package com.tkisor.nekujs.recipe;

import com.tkisor.nekujs.plugin.LegacyPluginDiscovery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class LegacyRecipeSchemaSourcesTest {
    @Test
    void readsOnlyOwnedSchemaResourcesFromJarsAndDevelopmentDirectories(@TempDir Path directory) throws Exception {
        Path jar = directory.resolve("source.jar");
        try (var output = new JarOutputStream(Files.newOutputStream(jar))) {
            for (String resource : List.of("data/create/kubejs/recipe_schema/z.json",
                    "data/create/kubejs/recipe_schema/base/a.json", "data/create/recipe/not_a_schema.json",
                    "assets/create/kubejs/recipe_schema/not_data.json")) {
                output.putNextEntry(new JarEntry(resource));
                output.write("{\"parent\":\"create:base/processing\"}".getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        Path root = directory.resolve("development");
        Path schema = root.resolve("data/mekanism/kubejs/recipe_schema/crushing.json");
        Files.createDirectories(schema.getParent());
        Files.writeString(schema, "{\"parent\":\"mekanism:base/item_to_item\"}");
        var documents = LegacyRecipeSchemaSources.read(List.of(new LegacyPluginDiscovery.Source("jar-owner", jar),
                new LegacyPluginDiscovery.Source("development-owner", root)));
        assertEquals(List.of("create:base/a", "create:z", "mekanism:crushing"),
                documents.stream().map(document -> document.id()).toList());
        assertEquals("jar-owner", documents.getFirst().source());
        assertEquals("data/create/kubejs/recipe_schema/base/a.json", documents.getFirst().resourcePath());
        assertEquals("development-owner", documents.getLast().source());
        assertEquals("mekanism:base/item_to_item", documents.getLast().json().get("parent").getAsString());
        Files.delete(jar);
        assertFalse(Files.exists(jar), "Archive resources must be closed after collection");
    }

    @Test
    void missingSourcesAndNonObjectProfilesDoNotPublishPartialResults(@TempDir Path directory) throws Exception {
        assertThrows(java.io.IOException.class, () -> LegacyRecipeSchemaSources.read(
                List.of(new LegacyPluginDiscovery.Source("missing", directory.resolve("missing.jar")))));
        Path schema = directory.resolve("data/test/kubejs/recipe_schema/not_object.json");
        Files.createDirectories(schema.getParent());
        Files.writeString(schema, "[]");
        var failure = assertThrows(IllegalArgumentException.class, () -> LegacyRecipeSchemaSources.read(
                List.of(new LegacyPluginDiscovery.Source("test", directory))));
        assertTrue(failure.getMessage().contains("NEKO-5279"));
        assertTrue(failure.getMessage().contains("data/test/kubejs/recipe_schema/not_object.json"));
    }
}
