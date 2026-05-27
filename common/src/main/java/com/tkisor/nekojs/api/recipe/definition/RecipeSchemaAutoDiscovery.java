package com.tkisor.nekojs.api.recipe.definition;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Auto-discovers recipe type schemas from a Minecraft recipe registry.
 * Platform code provides the actual registry scanner; this class is the common logic.
 */
public final class RecipeSchemaAutoDiscovery {
    private RecipeSchemaAutoDiscovery() {}

    /**
     * Builds a RecipeTypeDefinitionRegistry from discovered recipe types.
     * Platform code should call this with a scanner that provides all known recipe types.
     */
    public static RecipeTypeDefinitionRegistry discover(Supplier<DiscoveredRecipeTypes> scanner) {
        DiscoveredRecipeTypes discovered = scanner.get();
        RecipeTypeDefinitionRegistry.Builder builder = RecipeTypeDefinitionRegistry.builder();

        for (var entry : discovered.types().entrySet()) {
            String fullType = entry.getKey(); // e.g. "minecraft:crafting_shaped"
            int colon = fullType.indexOf(':');
            if (colon < 0) continue;

            String namespace = fullType.substring(0, colon);
            String name = fullType.substring(colon + 1);
            String prefix = namespace + "_" + name;

            // Build field definitions from discovered key info
            // Sort: OUTPUT first, INPUT second, OTHER last (so "result" comes before "ingredient")
            List<DiscoveredRecipeKey> sortedKeys = new ArrayList<>(entry.getValue());
            sortedKeys.sort((a, b) -> Integer.compare(keyRolePriority(a.name()), keyRolePriority(b.name())));

            Map<String, RecipeFieldDefinition> fields = new LinkedHashMap<>();
            List<String> orderedKeys = new ArrayList<>();

            for (DiscoveredRecipeKey key : sortedKeys) {
                RecipeFieldDefinition field = new RecipeFieldDefinition(
                        key.name(), key.name(), key.kind(), key.isList(), key.optional(), null
                );
                fields.put(key.name(), field);
                orderedKeys.add(key.name());
            }

            List<List<String>> constructors = buildConstructors(orderedKeys, sortedKeys);
            builder.add(new RecipeTypeDefinition(namespace, name, fullType, prefix, constructors, fields));
        }

        return builder.build();
    }

    private static int keyRolePriority(String name) {
        if (isOutputName(name)) return 0;
        if (isInputName(name)) return 1;
        return 2;
    }

    private static boolean isOutputName(String name) {
        return name.startsWith("result") || name.startsWith("output");
    }

    private static boolean isInputName(String name) {
        return name.startsWith("ingredient") || name.equals("ingredients") || name.equals("inputs")
                || name.equals("key") || name.equals("pattern")
                || name.equals("template") || name.equals("base") || name.equals("addition");
    }

    private static List<List<String>> buildConstructors(List<String> orderedKeys, List<DiscoveredRecipeKey> keyInfos) {
        List<List<String>> constructors = new ArrayList<>();

        // Count required keys
        int requiredCount = 0;
        for (DiscoveredRecipeKey key : keyInfos) {
            if (!key.optional()) requiredCount++;
        }

        if (requiredCount == 0) {
            constructors.add(List.copyOf(orderedKeys));
            return constructors;
        }

        // Constructor with all keys
        constructors.add(List.copyOf(orderedKeys));
        // Constructor with just required keys
        if (requiredCount < orderedKeys.size()) {
            constructors.add(List.copyOf(orderedKeys.subList(0, requiredCount)));
        }

        return constructors;
    }

    /**
     * Platform-specific recipe type scanner result.
     */
    public record DiscoveredRecipeTypes(Map<String, List<DiscoveredRecipeKey>> types) {
        public static DiscoveredRecipeTypes of(Map<String, List<DiscoveredRecipeKey>> types) {
            return new DiscoveredRecipeTypes(Map.copyOf(types));
        }
    }

    /**
     * Describes a single key/field in a discovered recipe type.
     */
    public record DiscoveredRecipeKey(
            String name,
            RecipeFieldKind kind,
            boolean optional,
            boolean isList
    ) {
        public static DiscoveredRecipeKey required(String name, RecipeFieldKind kind) {
            return new DiscoveredRecipeKey(name, kind, false, false);
        }

        public static DiscoveredRecipeKey optional(String name, RecipeFieldKind kind) {
            return new DiscoveredRecipeKey(name, kind, true, false);
        }

        public static DiscoveredRecipeKey list(DiscoveredRecipeKey inner) {
            return new DiscoveredRecipeKey(inner.name(), inner.kind(), inner.optional(), true);
        }
    }
}
