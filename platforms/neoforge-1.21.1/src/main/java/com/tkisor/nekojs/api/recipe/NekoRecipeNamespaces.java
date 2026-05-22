package com.tkisor.nekojs.api.recipe;

import com.tkisor.nekojs.api.MemberVisibilityQuery;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinitionRegistry;
import com.tkisor.nekojs.core.plugin.NekoPluginRuntime;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class NekoRecipeNamespaces {
    private static final Map<Class<?>, Set<String>> RECIPE_TYPES_BY_HANDLER = new ConcurrentHashMap<>();

    private NekoRecipeNamespaces() {}

    public static Object createHandler(String namespace, RecipeEventJS event) {
        RecipeNamespaceEntry<?> entry = NekoPluginRuntime.current().recipeNamespaces().get(namespace);
        if (entry == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Function<RecipeEventJS, Object> factory = (Function<RecipeEventJS, Object>) entry.factory();
        return factory.apply(event);
    }

    public static Set<String> getNamespaces() {
        return NekoPluginRuntime.current().recipeNamespaces().keySet();
    }

    public static Set<String> getNamespaces(RecipeTypeDefinitionRegistry definitions) {
        Set<String> namespaces = new LinkedHashSet<>(getNamespaces());
        if (definitions != null) namespaces.addAll(definitions.namespaces());
        return Collections.unmodifiableSet(namespaces);
    }

    public static boolean hasNamespace(String namespace, RecipeTypeDefinitionRegistry definitions) {
        return getHandlerClass(namespace) != null || definitions != null && definitions.hasNamespace(namespace);
    }

    public static @Nullable Class<?> getHandlerClass(String namespace) {
        RecipeNamespaceEntry<?> entry = NekoPluginRuntime.current().recipeNamespaces().get(namespace);
        return entry == null ? null : entry.handlerClass();
    }

    public static Set<String> getRecipeTypes(String namespace) {
        Class<?> handlerClass = getHandlerClass(namespace);
        return handlerClass == null ? Set.of() : getRecipeTypes(handlerClass);
    }

    public static Set<String> getRecipeTypes(String namespace, RecipeTypeDefinitionRegistry definitions) {
        Set<String> types = new LinkedHashSet<>(getRecipeTypes(namespace));
        if (definitions != null) types.addAll(definitions.types(namespace));
        return Collections.unmodifiableSet(types);
    }

    public static Set<String> getRecipeTypes(Class<?> handlerClass) {
        if (handlerClass == null) {
            return Set.of();
        }
        return RECIPE_TYPES_BY_HANDLER.computeIfAbsent(handlerClass, NekoRecipeNamespaces::recipeTypesForHandler);
    }

    public static boolean hasRecipeType(String namespace, String recipeType) {
        return getRecipeTypes(namespace).contains(recipeType);
    }

    public static boolean hasRecipeType(String namespace, String recipeType, RecipeTypeDefinitionRegistry definitions) {
        return getRecipeTypes(namespace, definitions).contains(recipeType);
    }

    public static Map<String, Class<?>> getHandlerClasses() {
        Map<String, Class<?>> handlerClasses = new LinkedHashMap<>();
        NekoPluginRuntime.current().recipeNamespaces().forEach((namespace, entry) -> handlerClasses.put(namespace, entry.handlerClass()));
        return Collections.unmodifiableMap(handlerClasses);
    }

    private static Set<String> recipeTypesForHandler(Class<?> handlerClass) {
        Set<String> recipeTypes = MemberVisibilityQuery.getVisibleMethods(handlerClass).entrySet().stream()
                .filter(entry -> Modifier.isPublic(entry.getValue().member().getModifiers()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        return Collections.unmodifiableSet(recipeTypes);
    }
}
