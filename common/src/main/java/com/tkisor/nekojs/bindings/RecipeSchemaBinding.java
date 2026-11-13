package com.tkisor.nekojs.bindings;

import com.tkisor.nekojs.api.catalog.RecipeNamespaceCatalogEntry;
import com.tkisor.nekojs.api.recipe.RecipeJsonTypeCatalog;
import com.tkisor.nekojs.api.recipe.RecipeNamespaceEntry;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinitionRegistry;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinitionStorage;
import com.tkisor.nekojs.api.plugin.NekoRuntimeAccess;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exposes recipe type schema discovery to scripts as a global binding.
 * Usage: RecipeSchema.namespaces / RecipeSchema.types(ns) / RecipeSchema.describe(ns, type)
 */
public class RecipeSchemaBinding {

    public List<String> namespaces() {
        return new ArrayList<>(current().namespaces());
    }

    public List<String> types(String namespace) {
        // Merge handler method names into the schema type names
        List<String> all = new ArrayList<>(current().types(namespace));
        RecipeNamespaceEntry entry = NekoRuntimeAccess.get().recipeNamespaces().get(namespace);
        if (entry != null) {
            // Handler method names also count as types
            for (var method : entry.handlerClass().getMethods()) {
                String name = method.getName();
                if (method.getDeclaringClass() != Object.class && !all.contains(name)) {
                    all.add(name);
                }
            }
        }
        return all;
    }

    /**
     * JSON catalog: the types declared in mod jar recipe JSON. There is no schema field, so
     * this list is for lookup only.
     */
    public List<String> jsonTypes(String namespace) {
        return new ArrayList<>(RecipeJsonTypeCatalog.types(namespace));
    }

    public Map<String, Object> describe(String namespace, String type) {
        var def = current().get(namespace, type);
        // Check whether a handler method exists
        boolean hasHandler = hasHandlerMethod(namespace, type);

        if (def == null && !hasHandler) {
            return Map.of("exists", false);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("exists", def != null || hasHandler);
        result.put("hasHandler", hasHandler);
        result.put("type", def != null ? def.type() : (namespace + ":" + type));
        result.put("idPrefix", def != null ? def.prefix() : (namespace + "_" + type));

        // Handler method signature; overrides the schema's generic constructor
        if (hasHandler) {
            List<Map<String, Object>> handlerFields = new ArrayList<>();
            List<List<String>> handlerCtorOpts = new ArrayList<>();
            collectHandlerMethods(namespace, type, handlerFields, handlerCtorOpts);
            result.put("handlerFields", handlerFields);
            result.put("handlerConstructors", handlerCtorOpts);
        }

        if (def != null) {
            List<Map<String, Object>> fields = new ArrayList<>();
            for (var f : def.fields().values()) {
                Map<String, Object> fm = new LinkedHashMap<>();
                fm.put("name", f.name());
                fm.put("kind", f.kind().name());
                fm.put("required", f.required());
                fm.put("array", f.array());
                fm.put("path", f.path());
                fields.add(fm);
            }
            result.put("fields", fields);

            List<List<String>> ctors = new ArrayList<>();
            for (var c : def.constructors()) {
                ctors.add(List.copyOf(c));
            }
            result.put("constructors", ctors);
        } else {
            result.put("fields", List.of());
            result.put("constructors", List.of());
        }
        return result;
    }

    // Handler method collection delegates to RecipeNamespaceEntry (shared implementation, so
    // reflection logic is not duplicated)
    private static void collectHandlerMethods(String namespace, String type, List<Map<String, Object>> handlerFields, List<List<String>> constructorOpts) {
        RecipeNamespaceEntry entry = NekoRuntimeAccess.get().recipeNamespaces().get(namespace);
        if (entry == null) return;
        var handlerMethods = RecipeNamespaceCatalogEntry.collectHandlerMethods(entry.handlerClass());
        for (var hm : handlerMethods) {
            if (!hm.methodName().equals(type)) continue;
            List<String> params = new ArrayList<>();
            for (var p : hm.params()) {
                params.add(typeName(p.type()) + (p.optional() ? "?" : ""));
            }
            constructorOpts.add(params);
            if (handlerFields.isEmpty()) {
                for (var p : hm.params()) {
                    Map<String, Object> fm = new LinkedHashMap<>();
                    fm.put("name", p.name());
                    fm.put("kind", toFieldKind(p.type()));
                    fm.put("required", !p.optional());
                    handlerFields.add(fm);
                }
            }
        }
    }

    private static String typeName(String type) {
        return switch (type) {
            case "string" -> "String";
            case "number" -> "Number";
            default -> type;
        };
    }

    private static String toFieldKind(String type) {
        return switch (type) {
            case "ItemStack" -> "ITEM_STACK";
            case "Ingredient" -> "INGREDIENT";
            default -> "JSON";
        };
    }

    private static boolean hasHandlerMethod(String namespace, String type) {
        RecipeNamespaceEntry entry = NekoRuntimeAccess.get().recipeNamespaces().get(namespace);
        if (entry == null) return false;
        for (var method : entry.handlerClass().getMethods()) {
            if (method.getDeclaringClass() != Object.class && method.getName().equals(type)) {
                return true;
            }
        }
        return false;
    }

    private static RecipeTypeDefinitionRegistry current() {
        return RecipeTypeDefinitionStorage.current();
    }
}
