package com.tkisor.nekojs.api.catalog;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        var grouped = reflectMethods(handlerClass);
        for (var entry : grouped.entrySet()) {
            List<RecipeHandlerMethodEntry.HandlerParam> bestParams = null;
            int bestTotal = 0;
            for (var m : entry.getValue()) {
                var params = extractParams(m);
                // Prefer the overload with the most params (full signature)
                if (params.size() >= bestTotal) {
                    bestParams = params;
                    bestTotal = params.size();
                    int required = requiredParamCount(m);
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
            Type generic = genericTypes[i];
            if (optional && generic instanceof ParameterizedType pt && pt.getRawType() == Optional.class) {
                if (pt.getActualTypeArguments()[0] instanceof Class<?> c) display = c;
                generic = pt.getActualTypeArguments()[0]; // unwrap Optional<X> → X
            }
            params.add(new RecipeHandlerMethodEntry.HandlerParam(
                    paramName(method, i, display), typeName(display), qualifiedName(display),
                    genericSignature(generic), optional));
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

    /**
     * FQN for the parameter's Java type, so the probe emits version-correct {@code java:...}
     * imports instead of a hard-coded MC-version package. {@code null} for primitives /
     * {@code String} / wrapper numbers — those map straight to TS primitives and need no alias.
     */
    private static String qualifiedName(Class<?> cls) {
        if (cls == Optional.class) return null;
        if (cls.isPrimitive()) return null;
        if (cls == String.class) return null;
        if (Number.class.isAssignableFrom(cls) || cls == Boolean.class) return null;
        return cls.getName();
    }

    /**
     * Generic-aware type signature preserving type arguments, e.g.
     * {@code List<Ingredient>} → {@code "java.util.List<net.minecraft.item.crafting.Ingredient>"}.
     * The probe parses this to render {@code $Ingredient_[]} / {@code { [key: string]: $V_ }}
     * instead of a bare {@code $List_}. Falls back to {@code java.lang.Object} for
     * wildcards / type variables (not expected on recipe handlers).
     */
    private static String genericSignature(Type type) {
        if (type instanceof ParameterizedType pt) {
            String raw = pt.getRawType().getTypeName();
            Type[] args = pt.getActualTypeArguments();
            StringBuilder sb = new StringBuilder(raw).append('<');
            for (int i = 0; i < args.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(genericSignature(args[i]));
            }
            return sb.append('>').toString();
        }
        if (type instanceof Class<?> c) return c.getName();
        return "java.lang.Object";
    }

    private static String simpleTypeName(Class<?> cls) {
        if (cls == Optional.class) return "any";
        String name = cls.getSimpleName();
        if (name.equals("RecipeJsonValue")) return "json";
        return name;
    }

    private static Map<String, List<Method>> reflectMethods(Class<?> clazz) {
        Map<String, List<Method>> methods = new LinkedHashMap<>();
        for (Method m : clazz.getMethods()) {
            if (m.getDeclaringClass() == Object.class) continue;
            methods.computeIfAbsent(m.getName(), k -> new ArrayList<>()).add(m);
        }
        return methods;
    }

    private static int requiredParamCount(Method m) {
        int count = 0;
        for (var p : m.getParameterTypes()) {
            if (p == Optional.class) break;
            count++;
        }
        return count;
    }
}
