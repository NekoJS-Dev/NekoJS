package com.tkisor.nekojs.bindings;

import com.tkisor.nekojs.api.recipe.RecipeNamespaceEntry;
import com.tkisor.nekojs.api.recipe.RecipeNamespaceRegister;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinitionRegistry;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinitionStorage;
import com.tkisor.nekojs.core.plugin.NekoPluginRuntime;

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
        // 合并 handler 方法名和 schema type 名
        List<String> all = new ArrayList<>(current().types(namespace));
        RecipeNamespaceEntry<?> entry = NekoPluginRuntime.current().recipeNamespaces().get(namespace);
        if (entry != null) {
            // handler 的方法名也算 type
            for (var method : entry.handlerClass().getMethods()) {
                String name = method.getName();
                if (method.getDeclaringClass() != Object.class && !all.contains(name)) {
                    all.add(name);
                }
            }
        }
        return all;
    }

    public Map<String, Object> describe(String namespace, String type) {
        var def = current().get(namespace, type);
        // 检查是否有 handler 方法
        boolean hasHandler = hasHandlerMethod(namespace, type);

        if (def == null && !hasHandler) {
            return Map.of("exists", false);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("exists", def != null || hasHandler);
        result.put("hasHandler", hasHandler);
        result.put("type", def != null ? def.type() : (namespace + ":" + type));
        result.put("idPrefix", def != null ? def.prefix() : (namespace + "_" + type));

        // handler 方法签名（覆盖 schema 的通用构造器）
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

    private static void collectHandlerMethods(String namespace, String type, List<Map<String, Object>> handlerFields, List<List<String>> constructorOpts) {
        RecipeNamespaceEntry<?> entry = NekoPluginRuntime.current().recipeNamespaces().get(namespace);
        if (entry == null) return;
        for (var method : entry.handlerClass().getMethods()) {
            if (method.getDeclaringClass() == Object.class || !method.getName().equals(type)) continue;
            List<String> params = new ArrayList<>();
            java.lang.reflect.Type[] genericTypes = method.getGenericParameterTypes();
            java.lang.reflect.Parameter[] javaParams = method.getParameters();
            for (int i = 0; i < javaParams.length; i++) {
                var param = javaParams[i];
                Class<?> rawType = method.getParameterTypes()[i];
                boolean optional = rawType == java.util.Optional.class;
                // 对 Optional<T>，提取内层 T 的类型名
                Class<?> displayType = rawType;
                if (optional && genericTypes[i] instanceof java.lang.reflect.ParameterizedType pt) {
                    var arg = pt.getActualTypeArguments()[0];
                    if (arg instanceof Class<?> c) displayType = c;
                }
                String pName = param.getName();
                if (pName.equals("arg0") || pName.equals("arg1") || pName.startsWith("arg")) {
                    pName = simpleName(displayType);
                }
                params.add(pName + (optional ? "?" : ""));
            }
            constructorOpts.add(params);
            // 为每个 handler 方法收集字段（取第一个来命名）
            if (handlerFields.isEmpty()) {
                for (int i = 0; i < javaParams.length; i++) {
                    Class<?> rawType = method.getParameterTypes()[i];
                    boolean opt = rawType == java.util.Optional.class;
                    Class<?> displayType = rawType;
                    if (opt && genericTypes[i] instanceof java.lang.reflect.ParameterizedType pt) {
                        var arg = pt.getActualTypeArguments()[0];
                        if (arg instanceof Class<?> c) displayType = c;
                    }
                    Map<String, Object> fm = new LinkedHashMap<>();
                    fm.put("name", simpleName(displayType));
                    fm.put("kind", kindName(displayType));
                    fm.put("required", !opt);
                    handlerFields.add(fm);
                }
            }
        }
    }

    private static String simpleName(Class<?> cls) {
        String name = cls.getSimpleName();
        if (name.equals("RecipeJsonValue")) return "json";
        if (name.equals("ItemStack")) return "ItemStack";
        if (name.equals("Ingredient")) return "Ingredient";
        if (name.equals("String")) return "String";
        if (name.equals("int") || name.equals("float") || name.equals("double")) return name;
        if (name.equals("List")) return "List";
        if (name.equals("Map")) return "Map";
        return name;
    }

    private static String kindName(Class<?> cls) {
        String name = cls.getSimpleName();
        if (name.equals("ItemStack")) return "ITEM_STACK";
        if (name.equals("Ingredient")) return "INGREDIENT";
        if (name.equals("RecipeJsonValue")) return "JSON";
        if (name.equals("String")) return "STRING";
        if (name.equals("List") || name.equals("Map")) return "JSON";
        if (name.equals("int") || name.equals("float") || name.equals("double")) return "NUMBER";
        return "JSON";
    }

    private static boolean hasHandlerMethod(String namespace, String type) {
        RecipeNamespaceEntry<?> entry = NekoPluginRuntime.current().recipeNamespaces().get(namespace);
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
