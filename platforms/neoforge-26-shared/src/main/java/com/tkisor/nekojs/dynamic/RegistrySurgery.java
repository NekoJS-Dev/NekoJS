package com.tkisor.nekojs.dynamic;

import com.mojang.serialization.Lifecycle;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Reflection surgery on frozen vanilla {@link MappedRegistry}s, ported from
 * Katton's {@code RegistryMutationUtil}.
 *
 * <p>Field names verified against the actual patched sources of BOTH target
 * mappings (26.1.2.71 and 26.2.0.57 — identical, Mojang mappings):
 * <ul>
 *   <li>{@code frozen} — {@code boolean}</li>
 *   <li>{@code unregisteredIntrusiveHolders} — {@code @Nullable Map<T, Holder.Reference<T>>}
 *       (NeoForge keeps this map alive after freeze; we inject one when null)</li>
 *   <li>{@code byId} — fastutil {@code ObjectList<Holder.Reference<T>>} (used as {@code List})</li>
 *   <li>{@code toId} — fastutil {@code Reference2IntMap<T>}</li>
 *   <li>{@code byLocation} — {@code Map<Identifier, Holder.Reference<T>>}</li>
 *   <li>{@code byKey} — {@code Map<ResourceKey<T>, Holder.Reference<T>>}</li>
 *   <li>{@code byValue} — identity {@code Map<T, Holder.Reference<T>>}</li>
 *   <li>{@code registrationInfos} — identity {@code Map<ResourceKey<T>, RegistrationInfo>}</li>
 *   <li>{@code registryLifecycle} — {@link Lifecycle}</li>
 *   <li>{@code Holder.Reference.tags} — {@code @Nullable Set<TagKey<T>>}</li>
 * </ul>
 *
 * <p>We toggle the {@code frozen} flag directly instead of calling NeoForge's
 * {@code unfreeze()}/{@code freeze()} pair: re-freezing would re-run bake
 * callbacks and re-validate bound tags, which is far more side-effect surface
 * than a runtime hot-registration needs.
 */
public final class RegistrySurgery {

    private static final Map<String, Field> FIELD_CACHE = new ConcurrentHashMap<>();

    private RegistrySurgery() {
    }

    private static Field field(Class<?> owner, String name) {
        return FIELD_CACHE.computeIfAbsent(owner.getName() + "#" + name, ignored -> {
            try {
                Field field = owner.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException e) {
                throw new IllegalStateException(
                        "MappedRegistry internals moved: field '" + name + "' not found on " + owner.getName(), e);
            }
        });
    }

    private static Object get(Object target, String name) {
        try {
            return field(target.getClass(), name).get(target);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot read MappedRegistry field '" + name + "'", e);
        }
    }

    private static void set(Object target, String name, Object value) {
        try {
            field(target.getClass(), name).set(target, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot write MappedRegistry field '" + name + "'", e);
        }
    }

    static boolean isFrozen(MappedRegistry<?> registry) {
        Object frozen = get(registry, "frozen");
        return frozen instanceof Boolean b && b;
    }

    static void setFrozen(MappedRegistry<?> registry, boolean frozen) {
        set(registry, "frozen", frozen);
    }

    /**
     * Runs {@code action} with the registry temporarily unfrozen, restoring the
     * previous frozen state in a {@code finally} block.
     */
    public static <T, R> R withUnfrozenRegistry(MappedRegistry<T> registry, Supplier<R> action) {
        boolean wasFrozen = isFrozen(registry);
        try {
            if (wasFrozen) {
                setFrozen(registry, false);
            }
            return action.get();
        } finally {
            if (wasFrozen) {
                setFrozen(registry, true);
            }
        }
    }

    /**
     * Like {@link #withUnfrozenRegistry} but additionally injects a temporary
     * identity map into {@code unregisteredIntrusiveHolders} when it is null
     * (needed by registries whose constructor did not opt into intrusive holders
     * but whose {@code register} path we drive with
     * {@link MappedRegistry#createIntrusiveHolder}). The original null is
     * restored afterwards.
     */
    @SuppressWarnings("unchecked")
    public static <T, R> R withUnfrozenAndHolders(MappedRegistry<T> registry, Supplier<R> action) {
        Map<T, Holder.Reference<T>> previous = (Map<T, Holder.Reference<T>>) get(registry, "unregisteredIntrusiveHolders");
        boolean injected = previous == null;
        if (injected) {
            set(registry, "unregisteredIntrusiveHolders", new IdentityHashMap<T, Holder.Reference<T>>());
        }
        try {
            return withUnfrozenRegistry(registry, action);
        } finally {
            if (injected) {
                set(registry, "unregisteredIntrusiveHolders", null);
            }
        }
    }

    /**
     * Binds an empty tag set on a freshly registered holder. Holders created
     * after the registry froze never went through {@code refreshTagsInHolders},
     * so their {@code tags} field is null and {@code Holder.Reference#is(TagKey)}
     * would throw "Tags not bound".
     */
    public static void clearHolderTags(Holder.Reference<?> holder) {
        set(holder, "tags", Set.of());
    }

    /**
     * Batch-unregisters {@code ids} from {@code registry} with full index surgery
     * (dense {@code byId} rebuild + {@code toId} resequencing + lifecycle
     * recalculation). Currently used only by the world-leave cleanup path, which
     * is not wired to any lifecycle event yet — kept ready for that batch.
     */
    public static <T> void unregisterAll(
            MappedRegistry<T> registry,
            List<Identifier> ids,
            Function<Identifier, ResourceKey<T>> resourceKey
    ) {
        if (ids.isEmpty()) return;
        withUnfrozenRegistry(registry, () -> {
            for (Identifier id : ids) {
                ResourceKey<T> key = resourceKey.apply(id);
                Holder.Reference<T> holder = registry.get(key).orElse(null);
                if (holder == null) continue;
                removeEntry(registry, key, holder, holder.value());
            }
            return null;
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> void removeEntry(
            MappedRegistry<T> registry,
            ResourceKey<T> key,
            Holder.Reference<T> holder,
            T value
    ) {
        Map<ResourceKey<T>, Holder.Reference<T>> byKey = (Map<ResourceKey<T>, Holder.Reference<T>>) get(registry, "byKey");
        Map<Identifier, Holder.Reference<T>> byLocation = (Map<Identifier, Holder.Reference<T>>) get(registry, "byLocation");
        Map<T, Holder.Reference<T>> byValue = (Map<T, Holder.Reference<T>>) get(registry, "byValue");
        List<Holder.Reference<T>> byId = (List<Holder.Reference<T>>) get(registry, "byId");
        Reference2IntMap<T> toId = (Reference2IntMap<T>) get(registry, "toId");
        Map<ResourceKey<T>, RegistrationInfo> registrationInfos =
                (Map<ResourceKey<T>, RegistrationInfo>) get(registry, "registrationInfos");

        int removedIndex = toId.removeInt(value);
        byKey.remove(key);
        byLocation.remove(key.identifier());
        byValue.remove(value);
        registrationInfos.remove(key);

        if (removedIndex >= 0 && removedIndex < byId.size() && byId.get(removedIndex) == holder) {
            byId.remove(removedIndex);
        } else {
            byId.remove(holder);
        }

        toId.clear();
        for (int index = 0; index < byId.size(); index++) {
            toId.put(byId.get(index).value(), index);
        }

        set(registry, "registryLifecycle", recalculateLifecycle(registrationInfos.values()));
        clearHolderTags(holder);
    }

    private static Lifecycle recalculateLifecycle(Iterable<RegistrationInfo> registrationInfos) {
        Lifecycle lifecycle = Lifecycle.stable();
        for (RegistrationInfo info : registrationInfos) {
            lifecycle = lifecycle.add(info.lifecycle());
        }
        return lifecycle;
    }
}
