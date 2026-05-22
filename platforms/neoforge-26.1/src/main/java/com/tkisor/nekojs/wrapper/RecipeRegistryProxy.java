package com.tkisor.nekojs.wrapper;

import com.tkisor.nekojs.api.recipe.NekoRecipeNamespaces;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinitionRegistry;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import graal.graalvm.polyglot.proxy.ProxyObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RecipeRegistryProxy implements ProxyObject {
    private static final String NAMESPACES = "namespaces";
    private static final String TYPES = "types";
    private static final String HAS_NAMESPACE = "hasNamespace";
    private static final String HAS_TYPE = "hasType";
    private static final List<String> HELPER_KEYS = List.of(NAMESPACES, TYPES, HAS_NAMESPACE, HAS_TYPE);

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
        if (handler != null) return handler;
        RecipeTypeDefinitionRegistry definitions = event.getRecipeTypeDefinitions();
        if (definitions.hasNamespace(namespace)) return new DataDrivenRecipeNamespaceProxy(event, namespace, definitions);
        return new FallbackNamespaceProxy(event, namespace);
    }

    private static String stringArgument(Value[] arguments, int index, String name) {
        if (arguments.length <= index || !arguments[index].isString()) {
            throw new IllegalArgumentException("Missing recipe " + name + " argument");
        }
        return arguments[index].asString();
    }
}