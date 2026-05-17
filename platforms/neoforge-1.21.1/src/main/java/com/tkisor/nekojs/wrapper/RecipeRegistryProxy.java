package com.tkisor.nekojs.wrapper;

import com.tkisor.nekojs.api.recipe.NekoRecipeNamespaces;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import graal.graalvm.polyglot.proxy.ProxyObject;

import java.util.ArrayList;
import java.util.List;

public class RecipeRegistryProxy implements ProxyObject {
    private final RecipeEventJS event;

    public RecipeRegistryProxy(RecipeEventJS event) {
        this.event = event;
    }

    @Override
    public Object getMember(String namespace) {
        return switch (namespace) {
            case "namespaces" -> (ProxyExecutable) arguments -> namespaces();
            case "types" -> (ProxyExecutable) arguments -> types(stringArgument(arguments, 0, "namespace"));
            case "hasNamespace" -> (ProxyExecutable) arguments -> hasNamespace(stringArgument(arguments, 0, "namespace"));
            case "hasType" -> (ProxyExecutable) arguments -> hasType(stringArgument(arguments, 0, "namespace"), stringArgument(arguments, 1, "type"));
            default -> {
                Object handler = NekoRecipeNamespaces.createHandler(namespace, event);
                if (handler != null) yield handler;
                yield new FallbackNamespaceProxy(event, namespace);
            }
        };
    }

    public List<String> namespaces() {
        return new ArrayList<>(NekoRecipeNamespaces.getNamespaces());
    }

    public List<String> types(String namespace) {
        return new ArrayList<>(NekoRecipeNamespaces.getRecipeTypes(namespace));
    }

    public boolean hasNamespace(String namespace) {
        return NekoRecipeNamespaces.getHandlerClass(namespace) != null;
    }

    public boolean hasType(String namespace, String type) {
        return NekoRecipeNamespaces.hasRecipeType(namespace, type);
    }

    @Override
    public Object getMemberKeys() {
        List<String> keys = new ArrayList<>(NekoRecipeNamespaces.getNamespaces());
        keys.add("namespaces");
        keys.add("types");
        keys.add("hasNamespace");
        keys.add("hasType");
        return keys.toArray(String[]::new);
    }

    @Override
    public boolean hasMember(String key) { return true; }

    @Override
    public void putMember(String key, Value value) {}

    private static String stringArgument(Value[] arguments, int index, String name) {
        if (arguments.length <= index || !arguments[index].isString()) {
            throw new IllegalArgumentException("Missing recipe " + name + " argument");
        }
        return arguments[index].asString();
    }
}