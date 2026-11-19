package com.tkisor.nekojs.core.stage.gate;

import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.stage.StageScope;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry of inline recipe stage gates declared during {@code ServerEvents.recipes}
 * via {@code recipe.stage(stage, scope)} and {@code event.stage(filter, stage, scope)}.
 *
 * <p>Entries are keyed by {@link NekoId} recipe identifier so subsequent {@link #rekey(NekoId, NekoId)}
 * calls (for example when a script chains {@code .stage("iron_age").id("pack:custom_ingot")})
 * preserve the stage requirement under the final recipe ID. Registered {@link #onUpdated(Runnable)}
 * callbacks are notified whenever inline recipe gates change or when {@link #notifyUpdated()} is
 * invoked at the end of a datapack recipe reload, allowing the active {@link CompiledStageGateSnapshot}
 * to merge the latest inline recipe gates automatically.
 */
public final class InlineRecipeStageRegistry {

    /** Process-wide shared inline recipe stage registry for server recipe reloads. */
    public static final InlineRecipeStageRegistry SHARED = new InlineRecipeStageRegistry();

    private static final ThreadLocal<InlineRecipeStageRegistry> DRAFT = new ThreadLocal<>();

    public static InlineRecipeStageRegistry current() {
        InlineRecipeStageRegistry draft = DRAFT.get();
        return draft == null ? SHARED : draft;
    }

    public static <T> T withDraft(InlineRecipeStageRegistry draft, java.util.function.Supplier<T> action) {
        InlineRecipeStageRegistry previous = DRAFT.get();
        DRAFT.set(Objects.requireNonNull(draft));
        try {
            return action.get();
        } finally {
            if (previous == null) {
                DRAFT.remove();
            } else {
                DRAFT.set(previous);
            }
        }
    }

    public void replaceSnapshot(Map<NekoId, InlineEntry> snapshot, boolean notify) {
        synchronized (lock) {
            entries.clear();
            entries.putAll(snapshot);
        }
        if (notify) {
            fireListeners();
        }
    }

    /**
     * Immutable inline recipe stage entry recorded during recipe script execution.
     *
     * @param recipeId target recipe identifier
     * @param stage    required stage identifier
     * @param scope    evaluation scope (for example player or server)
     */
    public record InlineEntry(NekoId recipeId, String stage, StageScope scope) {
        public InlineEntry {
            Objects.requireNonNull(recipeId, "recipeId");
            if (stage == null || stage.isBlank()) {
                throw new IllegalArgumentException(
                        "[NEKO-4070] Inline recipe stage name must not be blank for recipe " + recipeId);
            }
            stage = stage.trim();
            Objects.requireNonNull(scope, "scope");
        }
    }

    private final Object lock = new Object();
    private final Map<NekoId, InlineEntry> entries = new LinkedHashMap<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private int batchDepth = 0;
    private boolean dirtyInBatch = false;

    /** Clears all recorded inline recipe stage entries and notifies registered listeners. */
    public void clear() {
        boolean shouldNotify;
        synchronized (lock) {
            boolean hadEntries = !entries.isEmpty();
            entries.clear();
            if (batchDepth > 0) {
                dirtyInBatch |= hadEntries;
                shouldNotify = false;
            } else {
                shouldNotify = true;
            }
        }
        if (shouldNotify) {
            fireListeners();
        }
    }

    /** Records or replaces an inline stage gate for {@code recipeId} using {@link StageScope#PLAYER}. */
    public void put(NekoId recipeId, String stage) {
        put(recipeId, stage, StageScope.PLAYER);
    }

    /** Records or replaces an inline stage gate for {@code recipeId}. */
    public void put(NekoId recipeId, String stage, StageScope scope) {
        InlineEntry entry = new InlineEntry(recipeId, stage, scope);
        boolean shouldNotify;
        synchronized (lock) {
            InlineEntry previous = entries.put(entry.recipeId(), entry);
            boolean changed = !entry.equals(previous);
            if (batchDepth > 0) {
                dirtyInBatch |= changed;
                shouldNotify = false;
            } else {
                shouldNotify = changed;
            }
        }
        if (shouldNotify) {
            fireListeners();
        }
    }

    /**
     * Moves any inline stage gate registered under {@code oldId} to {@code newId}.
     *
     * <p>If {@code oldId} has no inline stage gate or equals {@code newId}, this method is a no-op.
     */
    public void rekey(NekoId oldId, NekoId newId) {
        Objects.requireNonNull(oldId, "oldId");
        Objects.requireNonNull(newId, "newId");
        if (oldId.equals(newId)) {
            return;
        }
        boolean shouldNotify = false;
        synchronized (lock) {
            InlineEntry existing = entries.remove(oldId);
            if (existing != null) {
                entries.put(newId, new InlineEntry(newId, existing.stage(), existing.scope()));
                if (batchDepth > 0) {
                    dirtyInBatch = true;
                } else {
                    shouldNotify = true;
                }
            }
        }
        if (shouldNotify) {
            fireListeners();
        }
    }

    /** Removes any inline stage gate registered for {@code recipeId}. */
    public boolean remove(NekoId recipeId) {
        Objects.requireNonNull(recipeId, "recipeId");
        boolean removed;
        boolean shouldNotify = false;
        synchronized (lock) {
            removed = entries.remove(recipeId) != null;
            if (removed) {
                if (batchDepth > 0) {
                    dirtyInBatch = true;
                } else {
                    shouldNotify = true;
                }
            }
        }
        if (shouldNotify) {
            fireListeners();
        }
        return removed;
    }

    /** Returns the inline entry for {@code recipeId}, or {@code null} if none is registered. */
    public @Nullable InlineEntry get(NekoId recipeId) {
        Objects.requireNonNull(recipeId, "recipeId");
        synchronized (lock) {
            return entries.get(recipeId);
        }
    }

    /** Returns the number of registered inline recipe stage gates. */
    public int size() {
        synchronized (lock) {
            return entries.size();
        }
    }

    /** Returns {@code true} when no inline recipe stage gates are registered. */
    public boolean isEmpty() {
        synchronized (lock) {
            return entries.isEmpty();
        }
    }

    /** Returns an immutable insertion-ordered snapshot of all registered inline recipe stage gates. */
    public Map<NekoId, InlineEntry> snapshot() {
        synchronized (lock) {
            if (entries.isEmpty()) {
                return Map.of();
            }
            return Collections.unmodifiableMap(new LinkedHashMap<>(entries));
        }
    }

    /**
     * Executes {@code batchAction} while coalescing listener notifications into a single callback
     * invocation once the outermost batch finishes.
     */
    public void runBatch(Runnable batchAction) {
        Objects.requireNonNull(batchAction, "batchAction");
        synchronized (lock) {
            batchDepth++;
        }
        boolean shouldNotify = false;
        try {
            batchAction.run();
        } finally {
            synchronized (lock) {
                batchDepth--;
                if (batchDepth == 0 && dirtyInBatch) {
                    dirtyInBatch = false;
                    shouldNotify = true;
                }
            }
            if (shouldNotify) {
                fireListeners();
            }
        }
    }

    /** Registers a callback invoked whenever the inline recipe stage snapshot is updated. */
    public void onUpdated(Runnable listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** Unregisters a previously added update listener. */
    public void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    /** Explicitly notifies all registered update listeners. */
    public void notifyUpdated() {
        fireListeners();
    }

    private void fireListeners() {
        for (Runnable listener : listeners) {
            listener.run();
        }
    }
}
