//? if >=26 {
package com.tkisor.nekojs.util.selector;

import com.tkisor.nekojs.NekoJS;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Builds {@link EntitySelector} for 26.1 and 26.2. Both versions have the same 13-argument
 * constructor, but {@code MinMaxBounds} lives in a different package (26.1
 * {@code advancements.criterion}, 26.2 {@code advancements.predicates}), so 26-shared cannot
 * reference either FQN statically. The class resolves the {@code Doubles} factory from the FQN that
 * exists at runtime and calls the constructor through reflection.
 * The 1.21.1 copy does not use this class; its FQN is unique, so it builds the selector statically.
 */
final class EntitySelectorFactory {

    private static final String[] DOUBLES_FQNS = {
            "net.minecraft.advancements.criterion.MinMaxBounds$Doubles",
            "net.minecraft.advancements.predicates.MinMaxBounds$Doubles"
    };

    private static final Class<?> DOUBLES = resolveDoubles();
    private static final Constructor<?> SELECTOR_CTOR = resolveCtor();
    private static final Method BETWEEN = factory("between", double.class, double.class);
    private static final Method AT_MOST = factory("atMost", double.class);
    private static final Method AT_LEAST = factory("atLeast", double.class);
    private static final Method BOUNDS = accessor("bounds");
    private static final Method BOUNDS_MAX = boundsMax();

    private EntitySelectorFactory() {
    }

    /** {@code MinMaxBounds.Doubles.between(min, max)}。 */
    static Object doublesBetween(double min, double max) {
        return invoke(BETWEEN, null, min, max);
    }

    /** {@code MinMaxBounds.Doubles.atMost(v)}。 */
    static Object doublesAtMost(double v) {
        return invoke(AT_MOST, null, v);
    }

    /** {@code MinMaxBounds.Doubles.atLeast(v)}。 */
    static Object doublesAtLeast(double v) {
        return invoke(AT_LEAST, null, v);
    }

    /**
     * Returns the upper bound of the range, used to compute the AABB padding; returns {@code null} when
     * there is no upper bound.
     */
    static Double maxOf(Object range) {
        if (range == null) {
            return null;
        }
        Object bounds = invoke(BOUNDS, range);
        if (bounds == null) {
            return null;
        }
        Object max = invoke(BOUNDS_MAX, bounds);
        return max instanceof Optional<?> optional ? (Double) optional.orElse(null) : (Double) max;
    }

    /**
     * Calls the 13-argument constructor through reflection (currentEntity=false / playerName=null /
     * entityUUID=null / usesSelector=false; the builder decides the rest). {@code range} is a Doubles
     * instance produced by this factory, or {@code null}.
     */
    static EntitySelector create(
            int maxResults,
            boolean includesEntities,
            boolean worldLimited,
            List<Predicate<Entity>> contextFreePredicates,
            Object range,
            Function<Vec3, Vec3> position,
            AABB aabb,
            BiConsumer<Vec3, List<? extends Entity>> order,
            EntityType<?> type
    ) {
        if (SELECTOR_CTOR == null) {
            throw new IllegalStateException("EntitySelector constructor not found on this version");
        }
        try {
            return (EntitySelector) SELECTOR_CTOR.newInstance(
                    maxResults,
                    includesEntities,
                    worldLimited,
                    contextFreePredicates,
                    range,
                    position,
                    aabb,
                    order,
                    false,
                    null,
                    null,
                    type,
                    false);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("failed to construct EntitySelector", e);
        }
    }

    private static Class<?> resolveDoubles() {
        for (String fqn : DOUBLES_FQNS) {
            try {
                return Class.forName(fqn);
            } catch (ClassNotFoundException ignored) {
                // Try the next FQN
            }
        }
        NekoJS.LOGGER.warn("MinMaxBounds$Doubles not found for either {} — distance selectors disabled",
                String.join(" / ", DOUBLES_FQNS));
        return null;
    }

    private static Constructor<?> resolveCtor() {
        for (Constructor<?> ctor : EntitySelector.class.getConstructors()) {
            if (ctor.getParameterCount() == 13) {
                return ctor;
            }
        }
        NekoJS.LOGGER.warn("EntitySelector 13-arg constructor not found — builder disabled");
        return null;
    }

    private static Method factory(String name, Class<?>... params) {
        if (DOUBLES == null) {
            return null;
        }
        try {
            return DOUBLES.getMethod(name, params);
        } catch (NoSuchMethodException e) {
            NekoJS.LOGGER.warn("MinMaxBounds$Doubles.{} not found", name, e);
            return null;
        }
    }

    private static Method accessor(String name) {
        if (DOUBLES == null) {
            return null;
        }
        try {
            return DOUBLES.getMethod(name);
        } catch (NoSuchMethodException e) {
            NekoJS.LOGGER.warn("MinMaxBounds$Doubles.{} not found", name, e);
            return null;
        }
    }

    private static Method boundsMax() {
        // Bounds<T>#max() -> Optional<T> (same shape in 26.1 and 26.2)
        if (BOUNDS == null) {
            return null;
        }
        try {
            return BOUNDS.getReturnType().getMethod("max");
        } catch (NoSuchMethodException e) {
            NekoJS.LOGGER.warn("MinMaxBounds$Bounds.max not found", e);
            return null;
        }
    }

    private static Object invoke(Method method, Object target, Object... args) {
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("failed to invoke " + method, e);
        }
    }
}
//?}
