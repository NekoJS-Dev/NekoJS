package com.tkisor.nekojs.wrapper;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.api.recipe.NekoRecipeNamespaces;
import static com.tkisor.nekojs.api.recipe.RecipeRegistryKeys.*;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import graal.graalvm.polyglot.proxy.ProxyObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Top-level {@code event.recipes} proxy（1.12.2 版，镜像 neoforge-26-shared 同款）。
 * 命名空间访问路由到 {@link RecipeNamespaceProxy}（handler → schema → JSON fallback 三级解析）。
 */
@Doc("The event.recipes proxy: namespace access creating typed recipe builders.")
@Doc("Built-in helpers: namespaces(), types(ns), hasNamespace(ns), hasType(ns, type), describeType(ns, type).")
public class RecipeRegistryProxy implements ProxyObject {
    private final RecipeEventJS event;
    private final Map<String, Object> members = new HashMap<>();

    /** Wraps the recipes event. */
    public RecipeRegistryProxy(RecipeEventJS event) {
        this.event = event;
    }

    /** Proxy member lookup. */
    @Doc("Resolves a namespace member or built-in helper; internal ProxyObject plumbing.")
    @Override
    public Object getMember(String namespace) {
        return switch (namespace) {
            case NAMESPACES -> (ProxyExecutable) arguments -> namespaces();
            case TYPES -> (ProxyExecutable) arguments -> types(stringArgument(arguments, 0, "namespace"));
            case HAS_NAMESPACE -> (ProxyExecutable) arguments -> hasNamespace(stringArgument(arguments, 0, "namespace"));
            case HAS_TYPE -> (ProxyExecutable) arguments -> hasType(stringArgument(arguments, 0, "namespace"), stringArgument(arguments, 1, "type"));
            case DESCRIBE -> (ProxyExecutable) arguments -> describeType(stringArgument(arguments, 0, "namespace"), stringArgument(arguments, 1, "type"));
            default -> members.computeIfAbsent(namespace, this::namespaceMember);
        };
    }

    /** Lists all recipe namespaces. */
    @Doc("Lists all recipe namespaces.")
    @Return("namespace strings like 'minecraft'; never null")
    public List<String> namespaces() {
        return new ArrayList<>(NekoRecipeNamespaces.getNamespaces(event.getRecipeTypeDefinitions()));
    }

    /** Lists recipe types within a namespace. */
    @Doc("Lists the recipe types within a namespace.")
    @Param(name = "namespace", value = "the namespace to inspect, e.g. 'minecraft'")
    @Return("type names within the namespace; never null")
    public List<String> types(String namespace) {
        return new ArrayList<>(NekoRecipeNamespaces.getRecipeTypes(namespace, event.getRecipeTypeDefinitions()));
    }

    /** Checks whether a namespace exists. */
    @Doc("Checks whether a recipe namespace exists.")
    @Param(name = "namespace", value = "the namespace to check")
    @Return("true if the namespace exists")
    public boolean hasNamespace(String namespace) {
        return NekoRecipeNamespaces.hasNamespace(namespace, event.getRecipeTypeDefinitions());
    }

    /** Checks whether a type exists in a namespace. */
    @Doc("Checks whether a recipe type exists within a namespace.")
    @Param(name = "namespace", value = "the namespace to check")
    @Param(name = "type", value = "the type name to check")
    @Return("true if the type exists")
    public boolean hasType(String namespace, String type) {
        return NekoRecipeNamespaces.hasRecipeType(namespace, type, event.getRecipeTypeDefinitions());
    }

    /** Describes a recipe type's schema. */
    @Doc("Describes a recipe type's schema: fields, kinds, required flags, and constructors.")
    @Param(name = "namespace", value = "the namespace to inspect")
    @Param(name = "type", value = "the type name to inspect")
    @Return("a map with exists/type/idPrefix/fields/constructors; exists is false for unknown types")
    public Map<String, Object> describeType(String namespace, String type) {
        var def = event.getRecipeTypeDefinitions().get(namespace, type);
        if (def == null) {
            return Map.of("exists", false, "namespace", namespace, "type", type);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("exists", true);
        result.put("type", def.type());
        result.put("idPrefix", def.prefix());

        List<Map<String, Object>> fieldList = new ArrayList<>();
        for (var field : def.fields().values()) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("name", field.name());
            f.put("kind", field.kind().name());
            f.put("required", field.required());
            f.put("array", field.array());
            f.put("path", field.path());
            fieldList.add(f);
        }
        result.put("fields", fieldList);

        List<List<String>> constructors = new ArrayList<>();
        for (var c : def.constructors()) {
            constructors.add(List.copyOf(c));
        }
        result.put("constructors", constructors);
        return result;
    }

    @Override
    public Object getMemberKeys() {
        List<String> keys = new ArrayList<>(NekoRecipeNamespaces.getNamespaces(event.getRecipeTypeDefinitions()));
        keys.addAll(HELPER_KEYS);
        return keys.toArray(String[]::new);
    }

    @Override
    public boolean hasMember(String key) { return true; }

    @Override
    public void putMember(String key, Value value) {
        throw new UnsupportedOperationException("Recipe namespaces are read-only");
    }

    private Object namespaceMember(String namespace) {
        return new RecipeNamespaceProxy(new RecipeEventSchemaHost(event), namespace,
                NekoRecipeNamespaces.createHandler(namespace, event),
                event.getRecipeTypeDefinitions());
    }

    private static String stringArgument(Value[] arguments, int index, String name) {
        if (arguments.length <= index || !arguments[index].isString()) {
            throw new IllegalArgumentException("Missing recipe " + name + " argument");
        }
        return arguments[index].asString();
    }
}
