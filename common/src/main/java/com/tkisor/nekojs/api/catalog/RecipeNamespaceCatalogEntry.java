package com.tkisor.nekojs.api.catalog;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Describes a recipe namespace for the catalog / type generation.
 *
 * @param handlerMethods detailed handler method signatures for NekoProbe type generation
 */
public record RecipeNamespaceCatalogEntry(
        String namespace,
        Class<?> handlerClass,
        List<String> recipeTypes,
        boolean fallbackSupported,
        List<String> examples,
        List<RecipeHandlerMethodEntry> handlerMethods,
        List<RecipeSchemaTypeEntry> schemaTypes
) {
    public RecipeNamespaceCatalogEntry {
        recipeTypes = List.copyOf(recipeTypes == null ? List.of() : recipeTypes);
        examples = List.copyOf(examples == null ? List.of() : examples);
        handlerMethods = List.copyOf(handlerMethods == null ? List.of() : handlerMethods);
        schemaTypes = List.copyOf(schemaTypes == null ? List.of() : schemaTypes);
    }

    public RecipeNamespaceCatalogEntry(String namespace, Class<?> handlerClass,
                                        List<String> recipeTypes, boolean fallbackSupported,
                                        List<String> examples, List<RecipeHandlerMethodEntry> handlerMethods) {
        this(namespace, handlerClass, recipeTypes, fallbackSupported, examples, handlerMethods, List.of());
    }

    public RecipeNamespaceCatalogEntry(String namespace, Class<?> handlerClass,
                                        List<String> recipeTypes, boolean fallbackSupported, List<String> examples) {
        this(namespace, handlerClass, recipeTypes, fallbackSupported, examples, List.of(), List.of());
    }

    public RecipeNamespaceCatalogEntry(String namespace, Class<?> handlerClass, boolean fallbackSupported, List<String> examples) {
        this(namespace, handlerClass, List.of(), fallbackSupported, examples, List.of(), List.of());
    }

    public static RecipeNamespaceCatalogEntry of(String namespace, Class<?> handlerClass) {
        return new RecipeNamespaceCatalogEntry(namespace, handlerClass, List.of(), true, List.of(), List.of(), List.of());
    }

    /** Collect handler + schema type info for NekoProbe type generation. */
    public static RecipeNamespaceCatalogEntry withHandlerMethods(
            String namespace, Class<?> handlerClass, List<String> recipeTypes,
            boolean fallbackSupported, List<String> examples) {
        return new RecipeNamespaceCatalogEntry(namespace, handlerClass, recipeTypes,
                fallbackSupported, examples, collectHandlerMethods(handlerClass));
    }

    /** Attach schema type info to an existing entry. */
    public RecipeNamespaceCatalogEntry withSchemaTypes(List<RecipeSchemaTypeEntry> types) {
        return new RecipeNamespaceCatalogEntry(namespace, handlerClass, recipeTypes,
                fallbackSupported, examples, handlerMethods, types);
    }

    public static List<RecipeHandlerMethodEntry> collectHandlerMethods(Class<?> handlerClass) {
        if (handlerClass == null) return List.of();
        List<RecipeHandlerMethodEntry> entries = new ArrayList<>();
        var grouped = new java.util.LinkedHashMap<String, List<Method>>();
        for (var m : handlerClass.getMethods()) {
            if (m.getDeclaringClass() == Object.class) continue;
            grouped.computeIfAbsent(m.getName(), k -> new ArrayList<>()).add(m);
        }
        for (var entry : grouped.entrySet()) {
            List<RecipeHandlerMethodEntry.HandlerParam> bestParams = null;
            int bestTotal = 0;
            for (var m : entry.getValue()) {
                var params = extractParams(m);
                // Prefer the overload with the most params (full signature)
                if (params.size() >= bestTotal) {
                    bestParams = params;
                    bestTotal = params.size();
                    int required = requiredCount(m);
                    entries.removeIf(e -> e.methodName().equals(entry.getKey()));
                    entries.add(new RecipeHandlerMethodEntry(entry.getKey(), params, required));
                }
            }
        }
        return List.copyOf(entries);
    }

    private static List<RecipeHandlerMethodEntry.HandlerParam> extractParams(Method method) {
        List<RecipeHandlerMethodEntry.HandlerParam> params = new ArrayList<>();
        var genericTypes = method.getGenericParameterTypes();
        var paramTypes = method.getParameterTypes();
        for (int i = 0; i < paramTypes.length; i++) {
            Class<?> raw = paramTypes[i];
            boolean optional = raw == Optional.class;
            Class<?> display = raw;
            if (optional && genericTypes[i] instanceof ParameterizedType pt) {
                if (pt.getActualTypeArguments()[0] instanceof Class<?> c) display = c;
            }
            params.add(new RecipeHandlerMethodEntry.HandlerParam(
                    paramName(method, i, display), typeName(display), optional));
        }
        return params;
    }

    private static String paramName(Method method, int index, Class<?> type) {
        var params = method.getParameters();
        if (index < params.length && !params[index].getName().startsWith("arg")) {
            return params[index].getName();
        }
        return simpleTypeName(type);
    }

    private static String typeName(Class<?> cls) {
        String raw = simpleTypeName(cls);
        // Map common Java types to JS-friendly names
        return switch (raw) {
            case "String" -> "string";
            case "ItemStack" -> "ItemStack";
            case "Ingredient" -> "Ingredient";
            case "RecipeJsonValue" -> "json";
            case "int", "float", "double" -> "number";
            case "boolean" -> "boolean";
            default -> raw;
        };
    }

    private static String simpleTypeName(Class<?> cls) {
        if (cls == Optional.class) return "any";
        String name = cls.getSimpleName();
        if (name.equals("RecipeJsonValue")) return "json";
        return name;
    }

    private static int requiredCount(Method method) {
        int count = 0;
        for (var p : method.getParameterTypes()) {
            if (p == Optional.class) break;
            count++;
        }
        return count;
    }
}
