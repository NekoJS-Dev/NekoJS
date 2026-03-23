package com.tkisor.nekojs.wrapper;

import com.tkisor.nekojs.api.recipe.NekoRecipeNamespaces;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;

public class RecipeRegistryProxy implements ProxyObject {
    private final RecipeEventJS event;

    public RecipeRegistryProxy(RecipeEventJS event) {
        this.event = event;
    }

    @Override
    public Object getMember(String namespace) {
        Object handler = NekoRecipeNamespaces.createHandler(namespace, event);

        if (handler != null) return handler;
        return new FallbackNamespaceProxy(event, namespace);
    }

    @Override
    public Object getMemberKeys() { return new String[0]; }
    @Override
    public boolean hasMember(String key) { return true; }
    @Override
    public void putMember(String key, Value value) {}
}