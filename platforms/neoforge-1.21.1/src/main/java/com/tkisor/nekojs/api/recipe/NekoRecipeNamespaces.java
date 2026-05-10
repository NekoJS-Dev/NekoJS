package com.tkisor.nekojs.api.recipe;

import com.tkisor.nekojs.core.NekoJSPluginManager;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class NekoRecipeNamespaces {
    private static final Map<String, Function<RecipeEventJS, Object>> NAMESPACES = new LinkedHashMap<>();
    private static final Map<String, Class<?>> HANDLER_CLASSES = new LinkedHashMap<>();
    private static boolean initialized = false;

    private NekoRecipeNamespaces() {}

    static void registerEntry(RecipeNamespaceEntry<RecipeEventJS> entry) {
        if (NAMESPACES.containsKey(entry.namespace())) {
            throw new IllegalArgumentException("Recipe namespace '" + entry.namespace() + "' is already registered. Possible plugin conflict.");
        }
        NAMESPACES.put(entry.namespace(), entry.factory());
        HANDLER_CLASSES.put(entry.namespace(), entry.handlerClass());
    }

    public static synchronized Object createHandler(String namespace, RecipeEventJS event) {
        if (!initialized) {
            initialize();
        }
        Function<RecipeEventJS, Object> factory = NAMESPACES.get(namespace);
        return factory != null ? factory.apply(event) : null;
    }

    /**
     * Returns an unmodifiable set of all registered recipe namespaces.
     */
    public static synchronized Set<String> getNamespaces() {
        if (!initialized) {
            initialize();
        }
        return Collections.unmodifiableSet(NAMESPACES.keySet());
    }

    /**
     * Returns the handler class for the given namespace.
     */
    public static synchronized @Nullable Class<?> getHandlerClass(String namespace) {
        if (!initialized) {
            initialize();
        }
        return HANDLER_CLASSES.get(namespace);
    }

    public static synchronized Map<String, Class<?>> getHandlerClasses() {
        if (!initialized) {
            initialize();
        }
        return Collections.unmodifiableMap(HANDLER_CLASSES);
    }

    private static void initialize() {
        NekoJSPluginManager.getPlugins().forEach(plugin -> plugin.registerRecipeNamespaces(NekoRecipeNamespaces::registerEntry));
        initialized = true;
    }
}
