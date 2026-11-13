package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.data.JSTypeAdapterRegistry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Scans holder classes for {@code public static final Registry<T>} and
 * {@code ResourceKey<Registry<T>>} fields; a ResourceKey is resolved through the root registry into a
 * Registry object. For every registry type without a dedicated adapter it instantiates a
 * {@link SimpleRegistryBasedAdapter} and registers it, so any registry-typed script parameter accepts
 * a string id with no per-type code. This is the NekoJS counterpart of KubeJS
 * {@code RegistryType.Scanner}: there is no Mixin path here, so it uses static field reflection.
 *
 * <p>Deduplication: a target type that already has an adapter (hand-written, or registered earlier in
 * the same scan round) is skipped, so hand-written adapters keep their behavior. Dynamic adapters use
 * the default LOWEST precedence of {@code AbstractJSTypeAdapter} and always yield to hand-written ones.
 *
 * <p>Failures are silent and never block startup: an unreadable field, a null registry object, a type
 * argument that cannot be resolved (raw generic or nested wildcard), or a target type that is itself a
 * registry (the root registry {@code Registry<Registry<?>>}) is skipped and logged at debug level.
 * Mods and plugins contribute extra holder classes through
 * {@code installInto(registry, List.of(MyRegistries.class))}, with no new plugin hook.
 */
public final class RegistryAutoAdapterScanner {
    private static final Logger LOGGER = LoggerFactory.getLogger(RegistryAutoAdapterScanner.class);

    private RegistryAutoAdapterScanner() {}

    public static void installInto(JSTypeAdapterRegistry registry, List<Class<?>> holderClasses) {
        Set<Class<?>> covered = new HashSet<>();
        for (JSTypeAdapter<?> adapter : registry.view()) {
            covered.add(adapter.getTargetClass());
        }
        for (Class<?> holder : holderClasses) {
            for (Field field : holder.getDeclaredFields()) {
                installField(registry, covered, field);
            }
        }
    }

    private static void installField(JSTypeAdapterRegistry registry, Set<Class<?>> covered, Field field) {
        int mods = field.getModifiers();
        if (!Modifier.isStatic(mods) || !Modifier.isPublic(mods)) return;
        if (Registry.class.isAssignableFrom(field.getType())) {
            registerFromHolder(registry, covered, field.getGenericType(), () -> readStatic(field, Registry.class));
        } else if (ResourceKey.class.isAssignableFrom(field.getType())) {
            // The vanilla Registries class exposes only ResourceKeys, so resolve them through the root registry
            // into Registry objects before registering; skip silently when the root registry cannot resolve
            // them (bare JVM, no bootstrap)
            registerFromHolder(registry, covered, keyTargetRegistryType(field.getGenericType()),
                    () -> resolveFromRoot(readStatic(field, ResourceKey.class)));
        }
    }

    private static void registerFromHolder(JSTypeAdapterRegistry registry, Set<Class<?>> covered,
            Type registryType, java.util.function.Supplier<Registry<?>> source) {
        Class<?> valueType = registryValueType(registryType);
        if (valueType == null) return; // Raw generic, or a type argument that is not a Class
        if (Registry.class.isAssignableFrom(valueType)) return; // The root registry and the like
        if (!covered.add(valueType)) return; // A dedicated adapter already exists (hand-written adapters win)

        Registry<?> registryObject;
        try {
            registryObject = source.get();
        } catch (Throwable inaccessible) {
            LOGGER.debug("registry auto-adapter: skip field {}", valueType, inaccessible);
            return;
        }
        if (registryObject == null) return;

        register(registry, registryObject, valueType);
    }

    @SuppressWarnings("unchecked")
    private static <H> H readStatic(Field field, Class<H> expected) {
        try {
            return (H) field.get(null);
        } catch (Throwable inaccessible) {
            LOGGER.debug("registry auto-adapter: field {} unreadable", field, inaccessible);
            return null;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Registry<?> resolveFromRoot(ResourceKey<?> key) {
        if (key == null) return null;
        // REGISTRY is declared as Registry<? extends Registry<?>>: wildcard capture makes the generic call
        // impossible, so this calls it raw and accepts both return shapes (26.x get returns Optional,
        // older versions return a nullable object)
        Object resolved = ((Registry) BuiltInRegistries.REGISTRY).get(key);
        if (resolved instanceof java.util.Optional<?> optional) {
            return (Registry<?>) optional.orElse(null);
        }
        return (Registry<?>) resolved;
    }

    /** Returns the inner {@code Registry<T>} type of {@code ResourceKey<Registry<T>>}; null if the shape differs. */
    private static Type keyTargetRegistryType(Type genericType) {
        if (genericType instanceof ParameterizedType parameterized
                && parameterized.getActualTypeArguments()[0] instanceof ParameterizedType inner
                && Registry.class.isAssignableFrom((Class<?>) inner.getRawType())) {
            return inner;
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> void register(JSTypeAdapterRegistry registry, Registry<?> registryObject, Class<?> valueType) {
        try {
            registry.register(new SimpleRegistryBasedAdapter<>(
                    (Registry) registryObject, (Class) valueType, valueType.getSimpleName()));
            LOGGER.debug("registry auto-adapter: registered {} for {}", valueType.getName(),
                    valueType.getSimpleName());
        } catch (Throwable constructionFailure) {
            LOGGER.debug("registry auto-adapter: failed to construct adapter for {}", valueType, constructionFailure);
        }
    }

    /** Returns the first type argument of {@code Registry<T>}; returns null when it cannot be resolved. */
    private static Class<?> registryValueType(Type genericType) {
        if (genericType instanceof ParameterizedType parameterized) {
            Type argument = parameterized.getActualTypeArguments()[0];
            if (argument instanceof Class<?> valueClass) return valueClass;
        }
        return null;
    }
}
