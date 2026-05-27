package com.tkisor.nekojs.wrapper;

import com.tkisor.nekojs.api.recipe.NekoRecipeNamespaces;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinitionRegistry;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import graal.graalvm.polyglot.proxy.ProxyObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RecipeRegistryProxy implements ProxyObject {
    private static final String NAMESPACES = "namespaces";
    private static final String TYPES = "types";
    private static final String HAS_NAMESPACE = "hasNamespace";
    private static final String HAS_TYPE = "hasType";
    private static final String DESCRIBE = "describeType";
    private static final List<String> HELPER_KEYS = List.of(NAMESPACES, TYPES, HAS_NAMESPACE, HAS_TYPE, DESCRIBE);

    private final RecipeEventJS event;
    private final Map<String, Object> members = new HashMap<>();

    public RecipeRegistryProxy(RecipeEventJS event) {
        this.event = event;
    }

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

    public List<String> namespaces() {
        Set<String> namespaces = NekoRecipeNamespaces.getNamespaces(event.getRecipeTypeDefinitions());
        return new ArrayList<>(namespaces);
    }

    public List<String> types(String namespace) {
        return new ArrayList<>(NekoRecipeNamespaces.getRecipeTypes(namespace, event.getRecipeTypeDefinitions()));
    }

    public boolean hasNamespace(String namespace) {
        return NekoRecipeNamespaces.hasNamespace(namespace, event.getRecipeTypeDefinitions());
    }

    public boolean hasType(String namespace, String type) {
        return NekoRecipeNamespaces.hasRecipeType(namespace, type, event.getRecipeTypeDefinitions());
    }

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
        Object handler = NekoRecipeNamespaces.createHandler(namespace, event);
        RecipeTypeDefinitionRegistry definitions = event.getRecipeTypeDefinitions();
        boolean hasDefinitions = definitions.hasNamespace(namespace);

        if (handler == null) {
            if (hasDefinitions) return new DataDrivenRecipeNamespaceProxy(event, namespace, definitions);
            return new FallbackNamespaceProxy(event, namespace);
        }

        // 复合代理：handler 优先，未知 type fallback 到 schema
        Object schemaFallback = hasDefinitions ? new DataDrivenRecipeNamespaceProxy(event, namespace, definitions) : new FallbackNamespaceProxy(event, namespace);
        return new HandlerWithSchemaProxy(handler, (ProxyObject) schemaFallback);
    }

    private static String stringArgument(Value[] arguments, int index, String name) {
        if (arguments.length <= index || !arguments[index].isString()) {
            throw new IllegalArgumentException("Missing recipe " + name + " argument");
        }
        return arguments[index].asString();
    }
}