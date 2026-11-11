package com.tkisor.nekujs.recipe;

import com.google.gson.JsonParser;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldDefinition;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldKind;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldRole;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinition;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.jar.JarFile;

/** Imports one explicitly supported data schema; it never loads the source addon's Java plugin. */
public final class MekanismCrushingSchema {
    private static final String CHILD = "data/mekanism/kubejs/recipe_schema/crushing.json";
    private static final String PARENT = "data/mekanism/kubejs/recipe_schema/base/item_to_item.json";
    private static final String EXPECTED_CHILD = """
            {"parent":"mekanism:base/item_to_item"}
            """;
    private static final String EXPECTED_PARENT = """
            {"keys":[{"name":"output","role":"output","type":"item_stack"},
                     {"name":"input","role":"input","type":"flat_sized_ingredient"}],
             "unique":["output"]}
            """;

    private MekanismCrushingSchema() {}

    /**
     * Reads the supplied 2101.1.7 schema profile, rejecting unimplemented semantics or changed resources.
     * Both fields remain raw recipe JSON; KubeJS stack and ingredient coercion are not emulated.
     */
    public static RecipeTypeDefinition read(Path source) throws IOException {
        try (var jar = new JarFile(source.toFile())) {
            requireProfile(jar, CHILD, EXPECTED_CHILD);
            requireProfile(jar, PARENT, EXPECTED_PARENT);
        }
        var fields = new LinkedHashMap<String, RecipeFieldDefinition>();
        fields.put("output", new RecipeFieldDefinition("output", "output", RecipeFieldKind.JSON,
                false, false, null, RecipeFieldRole.OUTPUT));
        fields.put("input", new RecipeFieldDefinition("input", "input", RecipeFieldKind.JSON,
                false, false, null, RecipeFieldRole.INPUT));
        return new RecipeTypeDefinition("mekanism", "crushing", "mekanism:crushing", null,
                List.of(List.of("output", "input")), java.util.Collections.unmodifiableMap(fields), List.of("output"));
    }

    private static void requireProfile(JarFile jar, String name, String expected) throws IOException {
        var entry = jar.getJarEntry(name);
        if (entry == null) {
            throw new IOException("[NEKO-5190] Recipe schema source lacks resource: " + jar.getName() + "!/" + name);
        }
        try (var reader = new InputStreamReader(jar.getInputStream(entry), StandardCharsets.UTF_8)) {
            if (!JsonParser.parseReader(reader).equals(JsonParser.parseString(expected))) {
                throw new IOException("[NEKO-5191] Unsupported recipe schema profile: " + jar.getName() + "!/" + name);
            }
        } catch (RuntimeException failure) {
            throw new IOException("[NEKO-5192] Recipe schema source rejected: " + jar.getName() + "!/" + name, failure);
        }
    }
}
