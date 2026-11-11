package com.tkisor.nekujs.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tkisor.nekujs.plugin.LegacyPluginDiscovery;
import com.tkisor.nekujs.recipe.schema.SchemaDocument;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.jar.JarFile;

/** Reads owned schema resources without loading recipe classes or retaining open archives. */
public final class LegacyRecipeSchemaSources {
    private LegacyRecipeSchemaSources() {}

    public static List<SchemaDocument> read(List<LegacyPluginDiscovery.Source> sources) throws IOException {
        List<SchemaDocument> documents = new ArrayList<>();
        for (var source : sources) {
            Path root = source.root();
            if (Files.isDirectory(root)) {
                Path data = root.resolve("data");
                if (!Files.isDirectory(data)) continue;
                try (var paths = Files.walk(data)) {
                    for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                        String resource = root.relativize(path).toString().replace('\\', '/');
                        String id = schemaId(resource);
                        if (id == null) continue;
                        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                            documents.add(new SchemaDocument(source.owner(), id, resource, object(JsonParser.parseReader(reader), resource)));
                        }
                    }
                }
            } else {
                try (var jar = new JarFile(root.toFile())) {
                    var entries = jar.stream().filter(entry -> !entry.isDirectory() && schemaId(entry.getName()) != null)
                            .sorted(Comparator.comparing(java.util.jar.JarEntry::getName)).toList();
                    for (var entry : entries) {
                        try (var reader = new InputStreamReader(jar.getInputStream(entry), StandardCharsets.UTF_8)) {
                            documents.add(new SchemaDocument(source.owner(), schemaId(entry.getName()), entry.getName(),
                                    object(JsonParser.parseReader(reader), entry.getName())));
                        }
                    }
                }
            }
        }
        return List.copyOf(documents);
    }

    private static JsonObject object(com.google.gson.JsonElement json, String resource) {
        if (!json.isJsonObject()) throw new IllegalArgumentException("[NEKO-5279] Recipe schema must be an object: " + resource);
        return json.getAsJsonObject();
    }

    private static String schemaId(String resource) {
        if (!resource.startsWith("data/") || !resource.endsWith(".json")) return null;
        int namespaceEnd = resource.indexOf('/', 5);
        if (namespaceEnd < 0) return null;
        String marker = "/kubejs/recipe_schema/";
        if (!resource.startsWith(marker, namespaceEnd)) return null;
        String path = resource.substring(namespaceEnd + marker.length(), resource.length() - 5);
        if (path.isEmpty()) return null;
        return resource.substring(5, namespaceEnd) + ":" + path;
    }
}
