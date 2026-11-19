package com.tkisor.nekojs.core.stage;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.stage.StableStageChangeEvent;
import com.tkisor.nekojs.api.stage.StageEvents;
import com.tkisor.nekojs.api.stage.StageReadView;
import com.tkisor.nekojs.core.stage.StagePlatformHooks.ReplacementChange;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Client-side mirror of the local player's stages, global server stages, and stage-locked item
 * and recipe visibility metadata pushed from the server.
 *
 * <p>Lives in {@code common} with zero Minecraft imports so both client scripts
 * ({@code Stages.clientPlayer()}, {@code Stages.clientServer()}, {@code StageEvents.clientAdded},
 * {@code StageEvents.clientRemoved}) and loader-specific recipe viewer / tooltip integrations
 * share one authoritative client state singleton ({@link #SHARED}).
 */
public final class ClientStageMirror {

    /** Process-wide client stage mirror cleared on disconnect or world unload. */
    public static final ClientStageMirror SHARED = new ClientStageMirror();

    private final ReentrantLock lock = new ReentrantLock();

    private final Set<String> playerPermanent = new LinkedHashSet<>();
    private final Map<String, Long> playerTemporary = new LinkedHashMap<>();

    private final Set<String> serverPermanent = new LinkedHashSet<>();
    private final Map<String, Long> serverTemporary = new LinkedHashMap<>();

    private Set<NekoId> hiddenItems = Set.of();
    private Set<NekoId> hiddenRecipes = Set.of();
    private Map<NekoId, String> lockedItemNames = Map.of();
    private Map<NekoId, List<String>> itemRequiredStages = Map.of();

    private volatile String localPlayerUuid = "";
    private volatile String localPlayerName = "";

    private volatile @Nullable Runnable primaryViewerRefreshListener;
    private final List<Runnable> additionalViewerRefreshListeners = new CopyOnWriteArrayList<>();

    private final StageReadView playerReadView = new MirrorReadView(true);
    private final StageReadView serverReadView = new MirrorReadView(false);

    /** Package-visible constructor for isolated unit test instances; production code uses {@link #SHARED}. */
    public ClientStageMirror() {
    }

    /** Returns a read-only view of the local client player's synchronized stages. */
    public StageReadView playerView() {
        return playerReadView;
    }

    /** Returns a read-only view of the client's synchronized server stages. */
    public StageReadView serverView() {
        return serverReadView;
    }

    /** Records the local client player's identity used when constructing client stage change events. */
    public void setLocalPlayer(@Nullable String playerUuid, @Nullable String playerName) {
        this.localPlayerUuid = playerUuid == null ? "" : playerUuid.trim();
        this.localPlayerName = playerName == null ? "" : playerName;
    }

    /** Registers or clears the primary recipe-viewer refresh callback invoked on stage/visibility updates. */
    public void setViewerRefreshListener(@Nullable Runnable viewerRefreshListener) {
        this.primaryViewerRefreshListener = viewerRefreshListener;
    }

    /** Adds an additional recipe-viewer or UI refresh listener. */
    public void addViewerRefreshListener(Runnable listener) {
        additionalViewerRefreshListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** Removes a previously added viewer refresh listener. */
    public void removeViewerRefreshListener(Runnable listener) {
        additionalViewerRefreshListeners.remove(listener);
    }

    /** Returns an immutable set of item IDs currently hidden from recipe viewers on the client. */
    public Set<NekoId> hiddenItems() {
        lock.lock();
        try {
            return hiddenItems;
        } finally {
            lock.unlock();
        }
    }

    /** Checks whether {@code itemId} is currently hidden from recipe viewers on the client. */
    public boolean isItemHidden(@Nullable NekoId itemId) {
        if (itemId == null) {
            return false;
        }
        lock.lock();
        try {
            return hiddenItems.contains(itemId);
        } finally {
            lock.unlock();
        }
    }

    /** Replaces the client-visible set of hidden items and notifies viewer refresh listeners. */
    public void setHiddenItems(@Nullable Collection<NekoId> items) {
        lock.lock();
        try {
            this.hiddenItems = items == null || items.isEmpty()
                    ? Set.of()
                    : Collections.unmodifiableSet(new LinkedHashSet<>(items));
        } finally {
            lock.unlock();
        }
        notifyViewerRefresh();
    }

    /** Returns an immutable set of recipe IDs currently hidden from recipe viewers on the client. */
    public Set<NekoId> hiddenRecipes() {
        lock.lock();
        try {
            return hiddenRecipes;
        } finally {
            lock.unlock();
        }
    }

    /** Checks whether {@code recipeId} is currently hidden from recipe viewers on the client. */
    public boolean isRecipeHidden(@Nullable NekoId recipeId) {
        if (recipeId == null) {
            return false;
        }
        lock.lock();
        try {
            return hiddenRecipes.contains(recipeId);
        } finally {
            lock.unlock();
        }
    }

    /** Replaces the client-visible set of hidden recipes and notifies viewer refresh listeners. */
    public void setHiddenRecipes(@Nullable Collection<NekoId> recipes) {
        lock.lock();
        try {
            this.hiddenRecipes = recipes == null || recipes.isEmpty()
                    ? Set.of()
                    : Collections.unmodifiableSet(new LinkedHashSet<>(recipes));
        } finally {
            lock.unlock();
        }
        notifyViewerRefresh();
    }

    /** Returns an immutable map of locked item IDs to obfuscated/locked display names. */
    public Map<NekoId, String> lockedItemNames() {
        lock.lock();
        try {
            return lockedItemNames;
        } finally {
            lock.unlock();
        }
    }

    /** Returns the custom locked display name for {@code itemId}, or {@code null} if not masked. */
    public @Nullable String lockedItemName(@Nullable NekoId itemId) {
        if (itemId == null) {
            return null;
        }
        lock.lock();
        try {
            return lockedItemNames.get(itemId);
        } finally {
            lock.unlock();
        }
    }

    /** Replaces the client-visible locked item display name overrides and refreshes viewers. */
    public void setLockedItemNames(@Nullable Map<NekoId, String> names) {
        lock.lock();
        try {
            this.lockedItemNames = names == null || names.isEmpty()
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(names));
        } finally {
            lock.unlock();
        }
        notifyViewerRefresh();
    }

    /** Returns an immutable map of item IDs to required stage names synced to the client. */
    public Map<NekoId, List<String>> itemRequiredStages() {
        lock.lock();
        try {
            return itemRequiredStages;
        } finally {
            lock.unlock();
        }
    }

    /** Returns required stages synced for {@code itemId}, or an empty list if none. */
    public List<String> requiredStagesForItem(@Nullable NekoId itemId) {
        if (itemId == null) {
            return List.of();
        }
        lock.lock();
        try {
            return itemRequiredStages.getOrDefault(itemId, List.of());
        } finally {
            lock.unlock();
        }
    }

    /** Replaces the synced item-to-required-stages map and triggers viewer refresh. */
    public void setItemRequiredStages(@Nullable Map<NekoId, List<String>> itemStages) {
        lock.lock();
        try {
            if (itemStages == null || itemStages.isEmpty()) {
                this.itemRequiredStages = Map.of();
            } else {
                Map<NekoId, List<String>> copy = new LinkedHashMap<>(itemStages.size());
                for (Map.Entry<NekoId, List<String>> entry : itemStages.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null && !entry.getValue().isEmpty()) {
                        copy.put(entry.getKey(), List.copyOf(entry.getValue()));
                    }
                }
                this.itemRequiredStages = Collections.unmodifiableMap(copy);
            }
        } finally {
            lock.unlock();
        }
        notifyViewerRefresh();
    }

    /**
     * Atomically updates hidden items, hidden recipes, and locked item display names from a
     * client gate visibility packet and notifies viewer refresh listeners once.
     */
    public void applyVisibilitySnapshot(
            @Nullable Collection<NekoId> nextHiddenItems,
            @Nullable Collection<NekoId> nextHiddenRecipes,
            @Nullable Map<NekoId, String> nextLockedItemNames) {
        applyVisibilitySnapshot(nextHiddenItems, nextHiddenRecipes, nextLockedItemNames, null);
    }

    /**
     * Atomically updates hidden items, hidden recipes, locked item display names, and optional
     * item stage requirements, then notifies viewer refresh listeners once.
     */
    public void applyVisibilitySnapshot(
            @Nullable Collection<NekoId> nextHiddenItems,
            @Nullable Collection<NekoId> nextHiddenRecipes,
            @Nullable Map<NekoId, String> nextLockedItemNames,
            @Nullable Map<NekoId, List<String>> nextItemStages) {
        lock.lock();
        try {
            this.hiddenItems = nextHiddenItems == null || nextHiddenItems.isEmpty()
                    ? Set.of()
                    : Collections.unmodifiableSet(new LinkedHashSet<>(nextHiddenItems));
            this.hiddenRecipes = nextHiddenRecipes == null || nextHiddenRecipes.isEmpty()
                    ? Set.of()
                    : Collections.unmodifiableSet(new LinkedHashSet<>(nextHiddenRecipes));
            this.lockedItemNames = nextLockedItemNames == null || nextLockedItemNames.isEmpty()
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(nextLockedItemNames));
            if (nextItemStages != null) {
                Map<NekoId, List<String>> copy = new LinkedHashMap<>(nextItemStages.size());
                for (Map.Entry<NekoId, List<String>> entry : nextItemStages.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null && !entry.getValue().isEmpty()) {
                        copy.put(entry.getKey(), List.copyOf(entry.getValue()));
                    }
                }
                this.itemRequiredStages = Collections.unmodifiableMap(copy);
            }
        } finally {
            lock.unlock();
        }
        notifyViewerRefresh();
    }

    /**
     * Applies a full authoritative snapshot of both player and server stages (for example on login
     * or full resync) and triggers viewer refresh.
     */
    public void applyFullSnapshot(
            @Nullable Set<String> playerPerm,
            @Nullable Map<String, Long> playerTemp,
            @Nullable Set<String> serverPerm,
            @Nullable Map<String, Long> serverTemp) {
        applyFullSnapshot(playerPerm, playerTemp, serverPerm, serverTemp, false);
    }

    public void applyFullSnapshot(
            @Nullable Set<String> playerPerm,
            @Nullable Map<String, Long> playerTemp,
            @Nullable Set<String> serverPerm,
            @Nullable Map<String, Long> serverTemp,
            boolean stateOnly) {
        List<StableStageChangeEvent> removed = new ArrayList<>();
        List<StableStageChangeEvent> added = new ArrayList<>();
        lock.lock();
        try {
            if (stateOnly) {
                replaceSetAndMap(playerPermanent, playerTemporary, playerPerm, playerTemp);
                replaceSetAndMap(serverPermanent, serverTemporary, serverPerm, serverTemp);
            } else {
                replaceScopeSnapshot("player", playerPermanent, playerTemporary, playerPerm, playerTemp, removed, added);
                replaceScopeSnapshot("server", serverPermanent, serverTemporary, serverPerm, serverTemp, removed, added);
            }
        } finally {
            lock.unlock();
        }
        for (StableStageChangeEvent event : removed) StageEvents.CLIENT_REMOVED.post(event, event.getStage());
        for (StableStageChangeEvent event : added) StageEvents.CLIENT_ADDED.post(event, event.getStage());
        notifyViewerRefresh();
    }

    private void replaceScopeSnapshot(
            String scope, Set<String> permanent, Map<String, Long> temporary,
            @Nullable Set<String> nextPermanent, @Nullable Map<String, Long> nextTemporary,
            List<StableStageChangeEvent> removed, List<StableStageChangeEvent> added) {
        Set<String> oldPermanent = new LinkedHashSet<>(permanent);
        Set<String> oldTemporary = new LinkedHashSet<>(temporary.keySet());
        replaceSetAndMap(permanent, temporary, nextPermanent, nextTemporary);
        for (String stage : oldPermanent) {
            if (!permanent.contains(stage) && !temporary.containsKey(stage)) {
                removed.add(buildClientEvent(scope, stage, false, 0L, "replace"));
            }
        }
        for (String stage : oldTemporary) {
            if (!oldPermanent.contains(stage) && !permanent.contains(stage) && !temporary.containsKey(stage)) {
                removed.add(buildClientEvent(scope, stage, true, 0L, "replace"));
            }
        }
        for (String stage : permanent) {
            if (!oldPermanent.contains(stage) && !oldTemporary.contains(stage)) {
                added.add(buildClientEvent(scope, stage, false, -1L, "replace"));
            }
        }
        for (var entry : temporary.entrySet()) {
            String stage = entry.getKey();
            if (!permanent.contains(stage) && !oldPermanent.contains(stage) && !oldTemporary.contains(stage)) {
                added.add(buildClientEvent(scope, stage, true, entry.getValue(), "replace"));
            }
        }
    }

    /** Applies a player-scoped stage delta ({@code "add"}, {@code "remove"}, {@code "clear"}, or {@code "replace"}). */
    public void applyPlayerDelta(String action, @Nullable String stage, long remainingTicks) {
        applyDelta("player", action, stage, remainingTicks, null, null);
    }

    /** Applies a server-scoped stage delta ({@code "add"}, {@code "remove"}, {@code "clear"}, or {@code "replace"}). */
    public void applyServerDelta(
            String action,
            @Nullable String stage,
            long remainingTicks,
            @Nullable Set<String> fullServerPermanent,
            @Nullable Map<String, Long> fullServerTemporary) {
        applyDelta("server", action, stage, remainingTicks, fullServerPermanent, fullServerTemporary);
    }

    /**
     * Applies a stage delta for {@code scope} ({@code "player"} or {@code "server"}), posting
     * {@link StageEvents#CLIENT_ADDED} / {@link StageEvents#CLIENT_REMOVED} with {@code stage} as
     * the dispatch key and notifying registered viewer refresh listeners whenever state changes.
     */
    public void applyDelta(
            String scope,
            String action,
            @Nullable String stage,
            long remainingTicks,
            @Nullable Set<String> fullPermanent,
            @Nullable Map<String, Long> fullTemporary) {
        Objects.requireNonNull(action, "action");
        boolean isServer = "server".equalsIgnoreCase(scope);
        String canonicalScope = isServer ? "server" : "player";
        String normalizedAction = action.trim().toLowerCase(Locale.ROOT);

        switch (normalizedAction) {
            case "add" -> {
                if (stage == null || stage.isBlank()) {
                    return;
                }
                String key = stage.trim();
                boolean changed = false;
                boolean temporary;
                long effectiveTicks;

                lock.lock();
                try {
                    Set<String> perm = isServer ? serverPermanent : playerPermanent;
                    Map<String, Long> temp = isServer ? serverTemporary : playerTemporary;
                    if (remainingTicks > 0L) {
                        if (!perm.contains(key)) {
                            Long prev = temp.put(key, remainingTicks);
                            changed = prev == null || prev != remainingTicks;
                            temporary = true;
                            effectiveTicks = remainingTicks;
                        } else {
                            temporary = false;
                            effectiveTicks = -1L;
                        }
                    } else {
                        temp.remove(key);
                        changed = perm.add(key);
                        temporary = false;
                        effectiveTicks = -1L;
                    }
                } finally {
                    lock.unlock();
                }

                if (changed) {
                    StableStageChangeEvent event =
                            buildClientEvent(canonicalScope, key, temporary, effectiveTicks, "manual");
                    StageEvents.CLIENT_ADDED.post(event, key);
                    notifyViewerRefresh();
                }
            }
            case "remove", "expire" -> {
                if (stage == null || stage.isBlank()) {
                    return;
                }
                String key = stage.trim();
                boolean removed;
                boolean wasTemporary;

                lock.lock();
                try {
                    Set<String> perm = isServer ? serverPermanent : playerPermanent;
                    Map<String, Long> temp = isServer ? serverTemporary : playerTemporary;
                    boolean removedPerm = perm.remove(key);
                    Long removedTemp = temp.remove(key);
                    removed = removedPerm || removedTemp != null;
                    wasTemporary = !removedPerm && removedTemp != null;
                } finally {
                    lock.unlock();
                }

                if (removed) {
                    StableStageChangeEvent event =
                            buildClientEvent(canonicalScope, key, wasTemporary, 0L,
                                    "expire".equals(normalizedAction) ? "expired" : "manual");
                    StageEvents.CLIENT_REMOVED.post(event, key);
                    notifyViewerRefresh();
                }
            }
            case "clear" -> {
                List<RemovedClientEntry> cleared = new ArrayList<>();
                lock.lock();
                try {
                    Set<String> perm = isServer ? serverPermanent : playerPermanent;
                    Map<String, Long> temp = isServer ? serverTemporary : playerTemporary;
                    for (String s : perm) {
                        cleared.add(new RemovedClientEntry(s, false));
                    }
                    for (String s : temp.keySet()) {
                        if (!perm.contains(s)) {
                            cleared.add(new RemovedClientEntry(s, true));
                        }
                    }
                    perm.clear();
                    temp.clear();
                } finally {
                    lock.unlock();
                }

                if (!cleared.isEmpty()) {
                    for (RemovedClientEntry entry : cleared) {
                        StableStageChangeEvent event = buildClientEvent(
                                canonicalScope, entry.stage(), entry.temporary(), 0L, "clear");
                        StageEvents.CLIENT_REMOVED.post(event, entry.stage());
                    }
                    notifyViewerRefresh();
                }
            }
            case "replace" -> applyReplacement(canonicalScope, fullPermanent, fullTemporary, null);
            default -> throw new IllegalArgumentException(
                    "[NEKO-4070] Unknown client stage delta action: " + action);
        }
    }

    public void applyReplacement(
            String scope,
            @Nullable Set<String> fullPermanent,
            @Nullable Map<String, Long> fullTemporary,
            @Nullable List<ReplacementChange> changes) {
        boolean isServer = "server".equalsIgnoreCase(scope);
        String canonicalScope = isServer ? "server" : "player";
        Set<String> nextPerm = fullPermanent == null ? Set.of() : fullPermanent;
        Map<String, Long> nextTemp = fullTemporary == null ? Map.of() : fullTemporary;
        List<ReplacementChange> events = changes == null ? new ArrayList<>() : changes;
        boolean stateChanged;
        lock.lock();
        try {
            Set<String> perm = isServer ? serverPermanent : playerPermanent;
            Map<String, Long> temp = isServer ? serverTemporary : playerTemporary;
            stateChanged = !perm.equals(nextPerm) || !temp.equals(nextTemp);
            if (stateChanged) {
                if (changes == null) {
                    collectLegacyReplacementChanges(perm, temp, nextPerm, nextTemp, events);
                }
                replaceSetAndMap(perm, temp, nextPerm, nextTemp);
            }
        } finally {
            lock.unlock();
        }
        for (ReplacementChange change : events) {
            StableStageChangeEvent event = buildClientEvent(canonicalScope, change.stage(),
                    change.temporary(), change.remainingTicks(), "replace");
            if (change.removed()) {
                StageEvents.CLIENT_REMOVED.post(event, change.stage());
            } else {
                StageEvents.CLIENT_ADDED.post(event, change.stage());
            }
        }
        if (stateChanged) {
            notifyViewerRefresh();
        }
    }

    private static void collectLegacyReplacementChanges(
            Set<String> perm, Map<String, Long> temp, Set<String> nextPerm, Map<String, Long> nextTemp,
            List<ReplacementChange> events) {
        for (String stage : perm) {
            if (!nextPerm.contains(stage) && !nextTemp.containsKey(stage)) {
                events.add(new ReplacementChange(stage, false, 0L));
            }
        }
        for (String stage : temp.keySet()) {
            if (!perm.contains(stage) && !nextPerm.contains(stage) && !nextTemp.containsKey(stage)) {
                events.add(new ReplacementChange(stage, true, 0L));
            }
        }
        for (String stage : nextPerm) {
            if (!perm.contains(stage)) {
                events.add(new ReplacementChange(stage, false, -1L));
            }
        }
        for (var entry : nextTemp.entrySet()) {
            String stage = entry.getKey();
            Long ticks = entry.getValue();
            if (!nextPerm.contains(stage) && !perm.contains(stage)
                    && ticks != null && ticks > 0L && !Objects.equals(temp.get(stage), ticks)) {
                events.add(new ReplacementChange(stage, true, ticks));
            }
        }
    }

    /**
     * Decrements client-side temporary stage timers down to {@code 1L} for HUD display while
     * awaiting the server's authoritative expiration delta.
     */
    public void tickClient() {
        lock.lock();
        try {
            decrementToFloorOne(playerTemporary);
            decrementToFloorOne(serverTemporary);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Clears all mirrored stages and locked item/recipe visibility state on client disconnect or
     * world unload while preserving registered viewer refresh listeners.
     */
    public void clear() {
        lock.lock();
        try {
            playerPermanent.clear();
            playerTemporary.clear();
            serverPermanent.clear();
            serverTemporary.clear();
            hiddenItems = Set.of();
            hiddenRecipes = Set.of();
            lockedItemNames = Map.of();
            itemRequiredStages = Map.of();
            localPlayerUuid = "";
            localPlayerName = "";
        } finally {
            lock.unlock();
        }
    }

    /** Triggers all registered recipe-viewer refresh callbacks safely. */
    public void notifyViewerRefresh() {
        Runnable primary = this.primaryViewerRefreshListener;
        if (primary != null) {
            runViewerListenerSafely(primary);
        }
        for (Runnable listener : additionalViewerRefreshListeners) {
            runViewerListenerSafely(listener);
        }
    }

    private static void runViewerListenerSafely(Runnable listener) {
        try {
            listener.run();
        } catch (Throwable t) {
            NekoJS.LOGGER.error("[NEKO-4073] Client stage viewer refresh listener failed", t);
        }
    }

    private StableStageChangeEvent buildClientEvent(
            String scope, String stage, boolean temporary, long remainingTicks, String reason) {
        if ("server".equals(scope)) {
            return StableStageChangeEvent.forClient(
                    scope, stage, "", "", temporary, remainingTicks, reason, serverView());
        }
        return StableStageChangeEvent.forClient(
                scope, stage, localPlayerUuid, localPlayerName, temporary, remainingTicks, reason, playerView());
    }

    private static void replaceSetAndMap(
            Set<String> targetPerm,
            Map<String, Long> targetTemp,
            @Nullable Set<String> sourcePerm,
            @Nullable Map<String, Long> sourceTemp) {
        targetPerm.clear();
        targetTemp.clear();
        if (sourcePerm != null) {
            for (String stage : sourcePerm) {
                if (stage != null && !stage.isBlank()) {
                    targetPerm.add(stage.trim());
                }
            }
        }
        if (sourceTemp != null) {
            for (Map.Entry<String, Long> entry : sourceTemp.entrySet()) {
                String stage = entry.getKey();
                Long ticks = entry.getValue();
                if (stage != null && !stage.isBlank() && ticks != null && ticks > 0L) {
                    String trimmed = stage.trim();
                    if (!targetPerm.contains(trimmed)) {
                        targetTemp.put(trimmed, ticks);
                    }
                }
            }
        }
    }

    private static void decrementToFloorOne(Map<String, Long> temporary) {
        if (temporary.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Long> entry : temporary.entrySet()) {
            long current = entry.getValue();
            if (current > 1L) {
                entry.setValue(current - 1L);
            }
        }
    }

    private final class MirrorReadView implements StageReadView {
        private final boolean playerScope;

        MirrorReadView(boolean playerScope) {
            this.playerScope = playerScope;
        }

        @Override
        public boolean has(String stage) {
            if (stage == null || stage.isBlank()) {
                return false;
            }
            String key = stage.trim();
            lock.lock();
            try {
                Set<String> perm = playerScope ? playerPermanent : serverPermanent;
                Map<String, Long> temp = playerScope ? playerTemporary : serverTemporary;
                return perm.contains(key) || temp.containsKey(key);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public List<String> getAll() {
            lock.lock();
            try {
                Set<String> perm = playerScope ? playerPermanent : serverPermanent;
                Map<String, Long> temp = playerScope ? playerTemporary : serverTemporary;
                if (perm.isEmpty() && temp.isEmpty()) {
                    return List.of();
                }
                Set<String> union = new LinkedHashSet<>(perm.size() + temp.size());
                union.addAll(perm);
                union.addAll(temp.keySet());
                return List.copyOf(union);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public long remainingTicks(String stage) {
            if (stage == null || stage.isBlank()) {
                return 0L;
            }
            String key = stage.trim();
            lock.lock();
            try {
                Set<String> perm = playerScope ? playerPermanent : serverPermanent;
                Map<String, Long> temp = playerScope ? playerTemporary : serverTemporary;
                if (perm.contains(key)) {
                    return -1L;
                }
                Long ticks = temp.get(key);
                return ticks != null && ticks > 0L ? ticks : 0L;
            } finally {
                lock.unlock();
            }
        }
    }

    private record RemovedClientEntry(String stage, boolean temporary) {
    }
}
