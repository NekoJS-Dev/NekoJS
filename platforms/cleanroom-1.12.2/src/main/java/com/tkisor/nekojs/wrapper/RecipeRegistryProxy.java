package com.tkisor.nekojs.wrapper;

import com.tkisor.nekojs.api.recipe.NekoRecipeNamespaces;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyObject;

import java.util.Set;

/**
 * 1.12.2 RecipeRegistryProxy - exposes recipe namespaces as a GraalJS ProxyObject.
 *
 * <p>{@code event.recipes.<namespace>} routes to the namespace handler (e.g.
 * {@code minecraft} → {@code MinecraftRecipeHandler}) via
 * {@link NekoRecipeNamespaces#createHandler}. Method calls on the handler then go through
 * GraalJS host access, with parameters auto-converted by the registered type adapters
 * ({@code string → ItemStack} via ItemStackAdapter, container elements recursively, etc.).
 */
public class RecipeRegistryProxy implements ProxyObject {

    private final RecipeEventJS event;

    public RecipeRegistryProxy(RecipeEventJS event) {
        this.event = event;
    }

    @Override
    public Object getMember(String key) {
        return NekoRecipeNamespaces.createHandler(key, event);
    }

    @Override
    public Object getMemberKeys() {
        try {
            Set<String> namespaces = NekoRecipeNamespaces.getNamespaces();
            return namespaces.toArray(new String[0]);
        } catch (Exception e) {
            return new String[]{"minecraft"};
        }
    }

    @Override
    public boolean hasMember(String key) {
        try {
            return NekoRecipeNamespaces.getNamespaces().contains(key);
        } catch (Exception e) {
            return "minecraft".equals(key);
        }
    }

    @Override
    public void putMember(String key, Value value) {
        // recipe namespaces are read-only
    }
}
