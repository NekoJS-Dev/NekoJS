package com.tkisor.nekojs.api.recipe;

import com.tkisor.nekojs.core.NekoJSPluginManager;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public final class NekoRecipeNamespaces {
    private static final Map<String, Function<RecipeEventJS, Object>> NAMESPACES = new HashMap<>();
    private static boolean initialized = false;

    private NekoRecipeNamespaces() {}

    static void register(String namespace, Function<RecipeEventJS, Object> factory) {
        if (NAMESPACES.containsKey(namespace)) {
            throw new IllegalArgumentException("Recipe namespace '" + namespace + "' is already registered. Possible plugin conflict.");
        }
        NAMESPACES.put(namespace, factory);
    }

    public static synchronized Object createHandler(String namespace, RecipeEventJS event) {
        if (!initialized) {
            initialize();
        }
        Function<RecipeEventJS, Object> factory = NAMESPACES.get(namespace);
        return factory != null ? factory.apply(event) : null;
    }

    private static void initialize() {
        NekoJSPluginManager.getPlugins().forEach(plugin -> plugin.registerRecipeNamespaces(NekoRecipeNamespaces::register));
        initialized = true;
    }
}