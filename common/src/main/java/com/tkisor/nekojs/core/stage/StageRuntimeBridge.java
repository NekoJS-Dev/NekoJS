package com.tkisor.nekojs.core.stage;

import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.stage.StageHandleResolver;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Process-level runtime holder connecting the stateless {@link StagesBinding} and
 * {@link StageHandleResolver} to the active {@link StageStore} and item-stage gate lookup.
 *
 * <p>Defaults to a standalone in-memory {@link StageStore} before server startup (and after
 * server teardown) so unit tests and pre-server evaluations work out-of-the-box without requiring
 * a running Minecraft server.
 */
public final class StageRuntimeBridge {

    private static volatile StageStore activeStore = new StageStore(null, StagePlatformHooks.NOOP);
    private static volatile Function<NekoId, List<String>> itemRequiredStagesLookup = id -> List.of();

    static {
        installResolvers();
    }

    private StageRuntimeBridge() {
    }

    /** Ensures static initialization has registered resolvers into {@link StageHandleResolver}. */
    public static void ensureInitialized() {
        // Static initializer wires StageHandleResolver; re-install defensively in case StageHandleResolver.reset() ran.
        installResolvers();
    }

    /** Returns the currently active {@link StageStore} (never {@code null}). */
    public static StageStore store() {
        return activeStore;
    }

    /**
     * Installs {@code store} as the active stage store and wires {@link StageHandleResolver}.
     */
    public static void install(StageStore store) {
        activeStore = Objects.requireNonNull(store, "store");
        installResolvers();
    }

    /**
     * Installs both {@code store} and an item-required-stages lookup function.
     */
    public static void install(
            StageStore store,
            @Nullable Function<NekoId, List<String>> itemStagesLookup) {
        activeStore = Objects.requireNonNull(store, "store");
        setItemRequiredStagesLookup(itemStagesLookup);
        installResolvers();
    }

    /** Alias for {@link #install(StageStore)} for platform lifecycle callers. */
    public static void installStore(StageStore store) {
        install(store);
    }

    /**
     * Uninstalls {@code expected} if it is still the active store, replacing it with a fresh
     * in-memory {@link StageStore} and keeping {@link StageHandleResolver} active.
     */
    public static void uninstall(@Nullable StageStore expected) {
        if (expected != null && activeStore == expected) {
            activeStore = new StageStore(null, StagePlatformHooks.NOOP);
            installResolvers();
        }
    }

    /**
     * Resets the active store to a fresh in-memory {@link StageStore}, clears the item-stage
     * lookup, and ensures {@link StageHandleResolver} delegates to the fresh store.
     */
    public static void reset() {
        activeStore = new StageStore(null, StagePlatformHooks.NOOP);
        itemRequiredStagesLookup = id -> List.of();
        installResolvers();
    }

    /** Installs or replaces the function resolving required stage names for an item identifier. */
    public static void setItemRequiredStagesLookup(@Nullable Function<NekoId, List<String>> lookup) {
        itemRequiredStagesLookup = lookup != null ? lookup : (id -> List.of());
    }

    /** Returns the active item-stage lookup function. */
    public static Function<NekoId, List<String>> itemRequiredStagesLookup() {
        return itemRequiredStagesLookup;
    }

    /**
     * Returns an immutable list of stages required for {@code itemId}, querying the active gate
     * lookup first and falling back to {@link ClientStageMirror#SHARED} when empty.
     */
    public static List<String> requiredStagesForItem(@Nullable NekoId itemId) {
        if (itemId == null) {
            return List.of();
        }
        List<String> resolved = itemRequiredStagesLookup.apply(itemId);
        if (resolved != null && !resolved.isEmpty()) {
            return List.copyOf(resolved);
        }
        List<String> mirrored = ClientStageMirror.SHARED.requiredStagesForItem(itemId);
        return mirrored.isEmpty() ? List.of() : List.copyOf(mirrored);
    }

    private static void installResolvers() {
        StageHandleResolver.install(
                ref -> activeStore.playerHandle(ref),
                () -> activeStore.serverHandle()
        );
    }
}
