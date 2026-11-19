package com.tkisor.nekojs.core.stage;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.stage.StableStageChangeEvent;
import com.tkisor.nekojs.api.stage.StageDefinition;
import com.tkisor.nekojs.api.stage.StageEvents;
import com.tkisor.nekojs.api.stage.StageHandle;
import com.tkisor.nekojs.api.stage.StageNotifyOptions;
import com.tkisor.nekojs.api.stage.StagePlayerRef;
import com.tkisor.nekojs.api.stage.StageTickContext;
import com.tkisor.nekojs.core.stage.StagePlatformHooks.ReplacementChange;
import com.tkisor.nekojs.script.ScriptManager;
import graal.graalvm.polyglot.Context;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Thread-safe runtime stage store managing permanent and temporary stages for the server and
 * individual players (including lazy disk loading for offline players), active
 * {@link StageDefinition} metadata/callbacks, per-tick temporary countdowns, event publication,
 * and platform delta synchronization.
 *
 * <p>When {@code worldStageDir} is {@code null}, the store operates purely in memory for
 * standalone unit tests and pre-world-load evaluation.
 */
public final class StageStore {

    /** Maximum permitted character length of a stage identifier. */
    public static final int MAX_STAGE_NAME_LENGTH = 256;

    private final @Nullable Path worldStageDir;
    private final ReentrantLock lock = new ReentrantLock();
    private final StageBucket serverBucket = new StageBucket();
    private final Map<String, PlayerStageBucket> playerBuckets = new ConcurrentHashMap<>();
    private final StageHandle serverHandleInstance = new ServerStageHandle();

    private volatile StagePlatformHooks hooks;
    private volatile Map<String, StageDefinition> definitions = Map.of();
    private volatile boolean anyEveryTickDefinitions = false;

    /** Creates an in-memory stage store with {@link StagePlatformHooks#NOOP}. */
    public StageStore() {
        this(null, StagePlatformHooks.NOOP);
    }

    /** Creates a stage store bound to {@code worldStageDir} with {@link StagePlatformHooks#NOOP}. */
    public StageStore(@Nullable Path worldStageDir) {
        this(worldStageDir, StagePlatformHooks.NOOP);
    }

    /**
     * Creates a stage store bound to {@code worldStageDir} and {@code hooks}.
     * When {@code worldStageDir} is non-null, existing {@code server.json} stage data is loaded
     * immediately and validated (throwing {@code [NEKO-4072]} if corrupt).
     */
    public StageStore(@Nullable Path worldStageDir, @Nullable StagePlatformHooks hooks) {
        this.worldStageDir = worldStageDir;
        this.hooks = hooks != null ? hooks : StagePlatformHooks.NOOP;
        if (worldStageDir != null) {
            StagePersistence.StageData serverData = StagePersistence.readServer(worldStageDir);
            serverBucket.permanent.addAll(serverData.permanent());
            serverBucket.temporary.putAll(serverData.temporary());
            serverBucket.dirty = false;
        }
    }

    /**
     * Validates a stage identifier and returns its trimmed form.
     *
     * @throws IllegalArgumentException ({@code [NEKO-4070]}) when null, blank, or longer than 256 characters
     */
    public static String validateStageName(@Nullable String stage) {
        if (stage == null || stage.isBlank()) {
            throw new IllegalArgumentException(
                    "[NEKO-4070] Stage name must not be blank or exceed 256 chars");
        }
        String trimmed = stage.trim();
        if (trimmed.length() > MAX_STAGE_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "[NEKO-4070] Stage name must not be blank or exceed 256 chars");
        }
        return trimmed;
    }

    /** Returns the bound world stage directory, or {@code null} when operating purely in memory. */
    public @Nullable Path worldStageDir() {
        return worldStageDir;
    }

    /** Returns the active platform synchronization and notification hooks. */
    public StagePlatformHooks hooks() {
        return hooks;
    }

    /** Updates the active platform hooks (null resets to {@link StagePlatformHooks#NOOP}). */
    public void setHooks(@Nullable StagePlatformHooks hooks) {
        this.hooks = hooks != null ? hooks : StagePlatformHooks.NOOP;
    }

    /**
     * Atomically replaces the active customized {@link StageDefinition} map on script reload.
     */
    public void setDefinitions(@Nullable Map<String, StageDefinition> nextDefinitions) {
        if (nextDefinitions == null || nextDefinitions.isEmpty()) {
            this.definitions = Map.of();
            this.anyEveryTickDefinitions = false;
            return;
        }
        Map<String, StageDefinition> copy = new LinkedHashMap<>(nextDefinitions.size());
        boolean hasTick = false;
        for (Map.Entry<String, StageDefinition> entry : nextDefinitions.entrySet()) {
            StageDefinition def = entry.getValue();
            if (def != null) {
                copy.put(def.stage(), def);
                if (def.everyTick() != null) {
                    hasTick = true;
                }
            }
        }
        this.definitions = Collections.unmodifiableMap(copy);
        this.anyEveryTickDefinitions = hasTick;
    }

    /** Convenience overload accepting a collection of {@link StageDefinition}s. */
    public void setDefinitions(@Nullable Collection<StageDefinition> nextDefinitions) {
        if (nextDefinitions == null || nextDefinitions.isEmpty()) {
            setDefinitions(Map.of());
            return;
        }
        Map<String, StageDefinition> map = new LinkedHashMap<>(nextDefinitions.size());
        for (StageDefinition def : nextDefinitions) {
            if (def != null) {
                map.put(def.stage(), def);
            }
        }
        setDefinitions(map);
    }

    /** Returns an immutable view of currently active {@link StageDefinition}s. */
    public Map<String, StageDefinition> definitions() {
        return definitions;
    }

    /** Looks up the active {@link StageDefinition} for {@code stage}, or {@code null} if undefined. */
    public @Nullable StageDefinition definition(@Nullable String stage) {
        if (stage == null || stage.isBlank()) {
            return null;
        }
        return definitions.get(stage.trim());
    }

    /** Returns the live mutable server {@link StageHandle}. */
    public StageHandle serverHandle() {
        return serverHandleInstance;
    }

    /**
     * Returns the {@link StageHandle} for {@code ref}:
     * <ul>
     *   <li>{@link StageHandle#EMPTY} when {@code ref} is null, fake, or has a blank UUID</li>
     *   <li>{@link StageHandle#clientReadOnly} backed by {@link ClientStageMirror#SHARED} when {@code ref.clientSide()}</li>
     *   <li>A live server-side {@link StageHandle} lazily backed by disk storage otherwise</li>
     * </ul>
     */
    public StageHandle playerHandle(@Nullable StagePlayerRef ref) {
        if (ref == null || ref.fake() || ref.uuid().isBlank()) {
            return StageHandle.EMPTY;
        }
        if (ref.clientSide()) {
            return StageHandle.clientReadOnly(ClientStageMirror.SHARED.playerView());
        }
        PlayerStageBucket bucket = getOrLoadPlayerBucket(ref.uuid(), ref.name());
        return bucket.handle;
    }

    /**
     * Loads the player's stages if needed, updates their cached profile name, and sends a full
     * player + server stage snapshot via {@link StagePlatformHooks#syncFullToPlayer}.
     */
    public void onPlayerJoin(String playerUuid, @Nullable String playerName) {
        if (playerUuid == null || playerUuid.isBlank()) {
            return;
        }
        String normalizedUuid = playerUuid.trim();
        Set<String> playerPerm;
        Map<String, Long> playerTemp;
        Set<String> serverPerm;
        Map<String, Long> serverTemp;
        lock.lock();
        try {
            PlayerStageBucket bucket = getOrLoadPlayerBucketLocked(normalizedUuid, playerName);
            playerPerm = Set.copyOf(bucket.permanent);
            playerTemp = Map.copyOf(bucket.temporary);
            serverPerm = Set.copyOf(serverBucket.permanent);
            serverTemp = Map.copyOf(serverBucket.temporary);
        } finally {
            lock.unlock();
        }
        hooks.syncFullToPlayer(normalizedUuid, playerPerm, playerTemp, serverPerm, serverTemp);
    }

    /** Flushes dirty stage data for {@code playerUuid} when a player disconnects. */
    public void onPlayerLeave(String playerUuid) {
        if (playerUuid == null || playerUuid.isBlank()) {
            return;
        }
        savePlayer(playerUuid.trim());
    }

    /** Returns an immutable snapshot of permanent server stages. */
    public Set<String> serverPermanentSnapshot() {
        lock.lock();
        try {
            return Collections.unmodifiableSet(new LinkedHashSet<>(serverBucket.permanent));
        } finally {
            lock.unlock();
        }
    }

    /** Returns an immutable snapshot of temporary server stages and remaining ticks. */
    public Map<String, Long> serverTemporarySnapshot() {
        lock.lock();
        try {
            return Collections.unmodifiableMap(new LinkedHashMap<>(serverBucket.temporary));
        } finally {
            lock.unlock();
        }
    }

    /** Returns an immutable snapshot of permanent stages for {@code playerUuid} (loading from disk if needed). */
    public Set<String> playerPermanentSnapshot(String playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        lock.lock();
        try {
            PlayerStageBucket bucket = getOrLoadPlayerBucketLocked(playerUuid.trim(), null);
            return Collections.unmodifiableSet(new LinkedHashSet<>(bucket.permanent));
        } finally {
            lock.unlock();
        }
    }

    /** Returns an immutable snapshot of temporary stages for {@code playerUuid} (loading from disk if needed). */
    public Map<String, Long> playerTemporarySnapshot(String playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        lock.lock();
        try {
            PlayerStageBucket bucket = getOrLoadPlayerBucketLocked(playerUuid.trim(), null);
            return Collections.unmodifiableMap(new LinkedHashMap<>(bucket.temporary));
        } finally {
            lock.unlock();
        }
    }

    /** Flushes server stages to {@code server.json} if dirty and {@code worldStageDir} is configured. */
    public void saveServer() {
        if (worldStageDir == null) {
            return;
        }
        Set<String> permCopy;
        Map<String, Long> tempCopy;
        lock.lock();
        try {
            if (!serverBucket.dirty) {
                return;
            }
            permCopy = new LinkedHashSet<>(serverBucket.permanent);
            tempCopy = new LinkedHashMap<>(serverBucket.temporary);
            serverBucket.dirty = false;
        } finally {
            lock.unlock();
        }
        try {
            StagePersistence.writeServer(worldStageDir, permCopy, tempCopy);
        } catch (RuntimeException e) {
            lock.lock();
            try {
                serverBucket.dirty = true;
            } finally {
                lock.unlock();
            }
            throw e;
        }
    }

    /** Flushes stages for {@code playerUuid} to {@code players/<uuid>.json} if dirty and bound to disk. */
    public void savePlayer(String playerUuid) {
        if (worldStageDir == null || playerUuid == null || playerUuid.isBlank()) {
            return;
        }
        String uuid = playerUuid.trim();
        Set<String> permCopy;
        Map<String, Long> tempCopy;
        lock.lock();
        try {
            PlayerStageBucket bucket = playerBuckets.get(uuid);
            if (bucket == null || !bucket.dirty) {
                return;
            }
            permCopy = new LinkedHashSet<>(bucket.permanent);
            tempCopy = new LinkedHashMap<>(bucket.temporary);
            bucket.dirty = false;
        } finally {
            lock.unlock();
        }
        try {
            StagePersistence.writePlayer(worldStageDir, uuid, permCopy, tempCopy);
        } catch (RuntimeException e) {
            lock.lock();
            try {
                PlayerStageBucket bucket = playerBuckets.get(uuid);
                if (bucket != null) {
                    bucket.dirty = true;
                }
            } finally {
                lock.unlock();
            }
            throw e;
        }
    }

    /**
     * Flushes all dirty server and player stage records to disk when {@code worldStageDir} is non-null.
     */
    public void saveAll() {
        if (worldStageDir == null) {
            return;
        }
        saveServer();
        List<String> loadedUuids;
        lock.lock();
        try {
            loadedUuids = new ArrayList<>(playerBuckets.keySet());
        } finally {
            lock.unlock();
        }
        for (String uuid : loadedUuids) {
            savePlayer(uuid);
        }
    }

    /**
     * Advances server and loaded player stage timers by one server tick, running configured
     * {@link StageDefinition#everyTick()} callbacks, expiring temporary stages that reach {@code 0},
     * firing {@link StageEvents#EXPIRED} and {@link StageEvents#REMOVED} ({@code reason="expired"}),
     * and dispatching delta sync packets and notifications.
     */
    public void tickServer() {
        Map<String, StageDefinition> activeDefs = this.definitions;
        boolean checkPermanentTicks = this.anyEveryTickDefinitions;

        List<PendingTickCallback> tickCallbacks = new ArrayList<>();
        List<ExpiredServerEntry> expiredServerEntries = new ArrayList<>();
        List<ExpiredPlayerEntry> expiredPlayerEntries = new ArrayList<>();

        lock.lock();
        try {
            if (checkPermanentTicks) {
                for (String stage : serverBucket.permanent) {
                    StageDefinition def = activeDefs.get(stage);
                    if (def != null && def.everyTick() != null) {
                        tickCallbacks.add(new PendingTickCallback(
                                def.everyTick(), StageTickContext.forServer(stage, -1L)));
                    }
                }
            }

            if (!serverBucket.temporary.isEmpty()) {
                Iterator<Map.Entry<String, Long>> it = serverBucket.temporary.entrySet().iterator();
                List<String> newlyExpiredServer = null;
                while (it.hasNext()) {
                    Map.Entry<String, Long> entry = it.next();
                    String stage = entry.getKey();
                    long nextTicks = entry.getValue() - 1L;
                    serverBucket.dirty = true;
                    if (nextTicks > 0L) {
                        entry.setValue(nextTicks);
                        StageDefinition def = activeDefs.get(stage);
                        if (def != null && def.everyTick() != null) {
                            tickCallbacks.add(new PendingTickCallback(
                                    def.everyTick(), StageTickContext.forServer(stage, nextTicks)));
                        }
                    } else {
                        it.remove();
                        if (newlyExpiredServer == null) {
                            newlyExpiredServer = new ArrayList<>();
                        }
                        newlyExpiredServer.add(stage);
                    }
                }
                if (newlyExpiredServer != null) {
                    Set<String> fullServerPerm = Set.copyOf(serverBucket.permanent);
                    Map<String, Long> fullServerTemp = Map.copyOf(serverBucket.temporary);
                    for (String stage : newlyExpiredServer) {
                        expiredServerEntries.add(new ExpiredServerEntry(stage, fullServerPerm, fullServerTemp));
                    }
                }
            }

            for (PlayerStageBucket bucket : playerBuckets.values()) {
                if (!checkPermanentTicks && bucket.temporary.isEmpty()) {
                    continue;
                }
                String playerName = resolvePlayerName(bucket.uuid, bucket.lastKnownName);
                if (checkPermanentTicks) {
                    for (String stage : bucket.permanent) {
                        StageDefinition def = activeDefs.get(stage);
                        if (def != null && def.everyTick() != null) {
                            tickCallbacks.add(new PendingTickCallback(
                                    def.everyTick(),
                                    StageTickContext.forPlayer(stage, bucket.uuid, playerName, -1L)));
                        }
                    }
                }
                if (!bucket.temporary.isEmpty()) {
                    Iterator<Map.Entry<String, Long>> it = bucket.temporary.entrySet().iterator();
                    while (it.hasNext()) {
                        Map.Entry<String, Long> entry = it.next();
                        String stage = entry.getKey();
                        long nextTicks = entry.getValue() - 1L;
                        bucket.dirty = true;
                        if (nextTicks > 0L) {
                            entry.setValue(nextTicks);
                            StageDefinition def = activeDefs.get(stage);
                            if (def != null && def.everyTick() != null) {
                                tickCallbacks.add(new PendingTickCallback(
                                        def.everyTick(),
                                        StageTickContext.forPlayer(stage, bucket.uuid, playerName, nextTicks)));
                            }
                        } else {
                            it.remove();
                            expiredPlayerEntries.add(new ExpiredPlayerEntry(bucket.uuid, playerName, stage));
                        }
                    }
                }
            }
        } finally {
            lock.unlock();
        }

        StagePlatformHooks currentHooks = this.hooks;
        int syncedServerEntries = 0;
        int syncedPlayerEntries = 0;
        RuntimeException syncFailure = null;
        try {
            for (ExpiredServerEntry expired : expiredServerEntries) {
                currentHooks.syncServerDelta(
                        "expire", expired.stage(), 0L, expired.fullServerPerm(), expired.fullServerTemp());
                syncedServerEntries++;
            }
            for (ExpiredPlayerEntry expired : expiredPlayerEntries) {
                currentHooks.syncPlayerDelta(expired.uuid(), "expire", expired.stage(), 0L);
                syncedPlayerEntries++;
            }
        } catch (RuntimeException failure) {
            syncFailure = failure;
        }

        for (PendingTickCallback pending : tickCallbacks) {
            invokeCallbackSafely("everyTick", pending.context().getStage(), pending.callback(), pending.context());
        }

        for (ExpiredServerEntry expired : expiredServerEntries.subList(0, syncedServerEntries)) {
            String stage = expired.stage();
            StageDefinition def = activeDefs.get(stage);
            if (def != null && def.whenExpired() != null) {
                invokeCallbackSafely(
                        "whenExpired", stage, def.whenExpired(), StageTickContext.forServer(stage, 0L));
            }
            StableStageChangeEvent event = StableStageChangeEvent.forServer(stage, true, 0L, "expired");
            StageEvents.EXPIRED.post(event, stage);
            StageEvents.REMOVED.post(event, stage);
            currentHooks.notifyStageChange("server", null, stage, false, def, StageNotifyOptions.DEFAULT);
        }

        for (ExpiredPlayerEntry expired : expiredPlayerEntries.subList(0, syncedPlayerEntries)) {
            String stage = expired.stage();
            StageDefinition def = activeDefs.get(stage);
            if (def != null && def.whenExpired() != null) {
                invokeCallbackSafely(
                        "whenExpired",
                        stage,
                        def.whenExpired(),
                        StageTickContext.forPlayer(stage, expired.uuid(), expired.playerName(), 0L));
            }
            StableStageChangeEvent event = StableStageChangeEvent.forPlayer(
                    stage, expired.uuid(), expired.playerName(), true, 0L, "expired");
            StageEvents.EXPIRED.post(event, stage);
            StageEvents.REMOVED.post(event, stage);
            currentHooks.notifyStageChange(
                    "player", expired.uuid(), stage, false, def, StageNotifyOptions.DEFAULT);
        }
        if (syncFailure != null) throw syncFailure;
    }

    private PlayerStageBucket getOrLoadPlayerBucket(String rawUuid, @Nullable String hintName) {
        String uuid = rawUuid.trim();
        PlayerStageBucket existing = playerBuckets.get(uuid);
        if (existing != null) {
            if (hintName != null && !hintName.isBlank()) {
                existing.lastKnownName = hintName;
            }
            return existing;
        }
        lock.lock();
        try {
            return getOrLoadPlayerBucketLocked(uuid, hintName);
        } finally {
            lock.unlock();
        }
    }

    private PlayerStageBucket getOrLoadPlayerBucketLocked(String uuid, @Nullable String hintName) {
        PlayerStageBucket existing = playerBuckets.get(uuid);
        if (existing != null) {
            if (hintName != null && !hintName.isBlank()) {
                existing.lastKnownName = hintName;
            }
            return existing;
        }
        PlayerStageBucket created = new PlayerStageBucket(uuid, hintName != null ? hintName : "");
        if (worldStageDir != null) {
            StagePersistence.StageData data = StagePersistence.readPlayer(worldStageDir, uuid);
            created.permanent.addAll(data.permanent());
            created.temporary.putAll(data.temporary());
            created.dirty = false;
        }
        playerBuckets.put(uuid, created);
        return created;
    }

    private String resolvePlayerName(String uuid, String fallbackName) {
        String onlineName = hooks.resolveOnlinePlayerName(uuid);
        if (onlineName != null && !onlineName.isBlank()) {
            return onlineName;
        }
        return fallbackName != null ? fallbackName : "";
    }

    private static List<String> validateAllStageNames(@Nullable Collection<String> stages) {
        if (stages == null || stages.isEmpty()) {
            return List.of();
        }
        List<String> validated = new ArrayList<>(stages.size());
        for (String stage : stages) {
            validated.add(validateStageName(stage));
        }
        return validated;
    }

    private static void invokeCallbackSafely(
            String callbackKind,
            String stage,
            Consumer<StageTickContext> callback,
            StageTickContext context) {
        try {
            callback.accept(context);
        } catch (Throwable t) {
            NekoJS.LOGGER.error(
                    "[NEKO-4073] Stage '{}' {} callback failed for scope '{}'",
                    stage,
                    callbackKind,
                    context.getScope(),
                    t);
        }
    }

    private static class StageBucket {
        final Set<String> permanent = new LinkedHashSet<>();
        final Map<String, Long> temporary = new LinkedHashMap<>();
        boolean dirty = false;

        boolean hasNormalized(String stage) {
            return permanent.contains(stage) || temporary.containsKey(stage);
        }

        List<String> snapshotAll() {
            if (permanent.isEmpty() && temporary.isEmpty()) {
                return List.of();
            }
            Set<String> union = new LinkedHashSet<>(permanent.size() + temporary.size());
            union.addAll(permanent);
            union.addAll(temporary.keySet());
            return List.copyOf(union);
        }

        long remainingTicksNormalized(String stage) {
            if (permanent.contains(stage)) {
                return -1L;
            }
            Long ticks = temporary.get(stage);
            return ticks != null && ticks > 0L ? ticks : 0L;
        }
    }

    private final class PlayerStageBucket extends StageBucket {
        final String uuid;
        final StageHandle handle;
        volatile String lastKnownName;

        PlayerStageBucket(String uuid, String lastKnownName) {
            this.uuid = uuid;
            this.lastKnownName = lastKnownName;
            this.handle = new PlayerStageHandle(this);
        }
    }

    private abstract class BaseStageHandle implements StageHandle {

        protected abstract StageBucket bucket();

        protected abstract String scopeName();

        protected abstract @Nullable String playerUuidOrNull();

        protected abstract String playerNameOrEmpty();

        protected abstract void syncSingleDelta(String action, @Nullable String stage, long remainingTicks);

        @Override
        public boolean has(String stage) {
            if (stage == null || stage.isBlank()) {
                return false;
            }
            String key = stage.trim();
            lock.lock();
            try {
                return bucket().hasNormalized(key);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public List<String> getAll() {
            lock.lock();
            try {
                return bucket().snapshotAll();
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
                return bucket().remainingTicksNormalized(key);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public boolean add(String stage, @Nullable StageNotifyOptions options) {
            rejectCandidateMutation();
            String normalized = validateStageName(stage);
            StageNotifyOptions opts = options != null ? options : StageNotifyOptions.DEFAULT;
            StageDefinition def = definitions.get(normalized);
            if (def != null && def.temporary() && def.defaultTicks() > 0L) {
                return grantTemporary(normalized, def.defaultTicks(), opts);
            }

            lock.lock();
            try {
                StageBucket b = bucket();
                if (b.permanent.contains(normalized)) {
                    return false;
                }
                b.temporary.remove(normalized);
                b.permanent.add(normalized);
                b.dirty = true;
            } finally {
                lock.unlock();
            }

            afterGranted(normalized, false, -1L, "manual", def, opts);
            return true;
        }

        @Override
        public boolean addAll(Collection<String> stages, @Nullable StageNotifyOptions options) {
            List<String> validated = validateAllStageNames(stages);
            if (validated.isEmpty()) {
                return false;
            }
            boolean changed = false;
            for (String stage : validated) {
                changed |= add(stage, options);
            }
            return changed;
        }

        @Override
        public boolean grantTemporary(String stage, long ticks, @Nullable StageNotifyOptions options) {
            rejectCandidateMutation();
            String normalized = validateStageName(stage);
            if (ticks <= 0L) {
                throw new IllegalArgumentException(
                        "[NEKO-4070] Temporary stage ticks must be > 0, got: " + ticks);
            }
            StageNotifyOptions opts = options != null ? options : StageNotifyOptions.DEFAULT;

            lock.lock();
            try {
                StageBucket b = bucket();
                if (b.permanent.contains(normalized)) {
                    return false;
                }
                Long previous = b.temporary.put(normalized, ticks);
                if (previous != null && previous == ticks) {
                    return false;
                }
                b.dirty = true;
            } finally {
                lock.unlock();
            }

            StageDefinition def = definitions.get(normalized);
            afterGranted(normalized, true, ticks, "manual", def, opts);
            return true;
        }

        @Override
        public boolean remove(String stage, @Nullable StageNotifyOptions options) {
            rejectCandidateMutation();
            String normalized = validateStageName(stage);
            StageNotifyOptions opts = options != null ? options : StageNotifyOptions.DEFAULT;
            boolean wasTemporary;

            lock.lock();
            try {
                StageBucket b = bucket();
                boolean removedPerm = b.permanent.remove(normalized);
                Long removedTemp = b.temporary.remove(normalized);
                if (!removedPerm && removedTemp == null) {
                    return false;
                }
                wasTemporary = !removedPerm && removedTemp != null;
                b.dirty = true;
            } finally {
                lock.unlock();
            }

            StageDefinition def = definitions.get(normalized);
            syncSingleDelta("remove", normalized, 0L);
            StableStageChangeEvent event = createChangeEvent(normalized, wasTemporary, 0L, "manual");
            StageEvents.REMOVED.post(event, normalized);
            hooks.notifyStageChange(scopeName(), playerUuidOrNull(), normalized, false, def, opts);
            return true;
        }

        @Override
        public boolean removeAll(Collection<String> stages, @Nullable StageNotifyOptions options) {
            List<String> validated = validateAllStageNames(stages);
            if (validated.isEmpty()) {
                return false;
            }
            boolean changed = false;
            for (String stage : validated) {
                changed |= remove(stage, options);
            }
            return changed;
        }

        @Override
        public boolean clear() {
            rejectCandidateMutation();
            List<RemovedSnapshot> removed;
            lock.lock();
            try {
                StageBucket b = bucket();
                if (b.permanent.isEmpty() && b.temporary.isEmpty()) {
                    return false;
                }
                removed = new ArrayList<>(b.permanent.size() + b.temporary.size());
                for (String stage : b.permanent) {
                    removed.add(new RemovedSnapshot(stage, false));
                }
                for (String stage : b.temporary.keySet()) {
                    if (!b.permanent.contains(stage)) {
                        removed.add(new RemovedSnapshot(stage, true));
                    }
                }
                b.permanent.clear();
                b.temporary.clear();
                b.dirty = true;
            } finally {
                lock.unlock();
            }

            syncSingleDelta("clear", "", 0L);
            for (RemovedSnapshot item : removed) {
                StableStageChangeEvent event =
                        createChangeEvent(item.stage(), item.temporary(), 0L, "clear");
                StageEvents.REMOVED.post(event, item.stage());
            }
            return true;
        }

        @Override
        public boolean replace(Collection<String> stages) {
            rejectCandidateMutation();
            List<String> validated = validateAllStageNames(stages);
            Set<String> targetPermanent = new LinkedHashSet<>();
            Map<String, Long> targetTemporary = new LinkedHashMap<>();
            Map<String, StageDefinition> activeDefs = definitions;
            for (String stage : validated) {
                StageDefinition def = activeDefs.get(stage);
                if (def != null && def.temporary() && def.defaultTicks() > 0L) {
                    if (!targetPermanent.contains(stage)) {
                        targetTemporary.put(stage, def.defaultTicks());
                    }
                } else {
                    targetTemporary.remove(stage);
                    targetPermanent.add(stage);
                }
            }

            List<ReplacementChange> changes = new ArrayList<>();

            lock.lock();
            try {
                StageBucket b = bucket();
                if (b.permanent.equals(targetPermanent) && b.temporary.equals(targetTemporary)) {
                    return false;
                }

                for (String oldPerm : b.permanent) {
                    if (!targetPermanent.contains(oldPerm) && !targetTemporary.containsKey(oldPerm)) {
                        changes.add(new ReplacementChange(oldPerm, false, 0L));
                    }
                }
                for (String oldTemp : b.temporary.keySet()) {
                    if (!b.permanent.contains(oldTemp)
                            && !targetPermanent.contains(oldTemp)
                            && !targetTemporary.containsKey(oldTemp)) {
                        changes.add(new ReplacementChange(oldTemp, true, 0L));
                    }
                }

                for (String newPerm : targetPermanent) {
                    if (!b.permanent.contains(newPerm)) {
                        changes.add(new ReplacementChange(newPerm, false, -1L));
                    }
                }
                for (Map.Entry<String, Long> entry : targetTemporary.entrySet()) {
                    String newTemp = entry.getKey();
                    long ticks = entry.getValue();
                    Long prevTicks = b.temporary.get(newTemp);
                    if (!b.permanent.contains(newTemp) && (prevTicks == null || prevTicks != ticks)) {
                        changes.add(new ReplacementChange(newTemp, true, ticks));
                    }
                }

                b.permanent.clear();
                b.permanent.addAll(targetPermanent);
                b.temporary.clear();
                b.temporary.putAll(targetTemporary);
                b.dirty = true;
            } finally {
                lock.unlock();
            }

            hooks.syncReplacement(playerUuidOrNull(), Set.copyOf(targetPermanent), Map.copyOf(targetTemporary),
                    List.copyOf(changes));
            for (ReplacementChange item : changes) {
                if (item.removed()) {
                    StableStageChangeEvent event =
                            createChangeEvent(item.stage(), item.temporary(), 0L, "replace");
                    StageEvents.REMOVED.post(event, item.stage());
                    continue;
                }
                StageDefinition def = activeDefs.get(item.stage());
                if (def != null && def.whenGranted() != null) {
                    invokeCallbackSafely(
                            "whenGranted",
                            item.stage(),
                            def.whenGranted(),
                            createTickContext(item.stage(), item.remainingTicks()));
                }
                StableStageChangeEvent event = createChangeEvent(
                        item.stage(), item.temporary(), item.remainingTicks(), "replace");
                StageEvents.ADDED.post(event, item.stage());
            }
            return true;
        }

        private void afterGranted(
                String stage,
                boolean temporary,
                long remainingTicks,
                String reason,
                @Nullable StageDefinition def,
                StageNotifyOptions options) {
            syncSingleDelta("add", stage, remainingTicks);
            if (def != null && def.whenGranted() != null) {
                invokeCallbackSafely(
                        "whenGranted",
                        stage,
                        def.whenGranted(),
                        createTickContext(stage, remainingTicks));
            }
            StableStageChangeEvent event = createChangeEvent(stage, temporary, remainingTicks, reason);
            StageEvents.ADDED.post(event, stage);
            hooks.notifyStageChange(scopeName(), playerUuidOrNull(), stage, true, def, options);
        }

        private StableStageChangeEvent createChangeEvent(
                String stage, boolean temporary, long remainingTicks, String reason) {
            String uuid = playerUuidOrNull();
            if (uuid == null) {
                return StableStageChangeEvent.forServer(stage, temporary, remainingTicks, reason);
            }
            return StableStageChangeEvent.forPlayer(
                    stage, uuid, playerNameOrEmpty(), temporary, remainingTicks, reason);
        }

        private StageTickContext createTickContext(String stage, long remainingTicks) {
            String uuid = playerUuidOrNull();
            if (uuid == null) {
                return StageTickContext.forServer(stage, remainingTicks);
            }
            return StageTickContext.forPlayer(stage, uuid, playerNameOrEmpty(), remainingTicks);
        }
    }

    private static void rejectCandidateMutation() {
        Context context;
        try {
            context = Context.getCurrent();
        } catch (IllegalStateException noGuestContext) {
            return;
        }
        if (ScriptManager.isCandidateContext(context)) {
            throw new IllegalStateException("[NEKO-4070] Cannot modify live stages while preparing a script generation");
        }
    }

    private final class ServerStageHandle extends BaseStageHandle {

        @Override
        protected StageBucket bucket() {
            return serverBucket;
        }

        @Override
        protected String scopeName() {
            return "server";
        }

        @Override
        protected @Nullable String playerUuidOrNull() {
            return null;
        }

        @Override
        protected String playerNameOrEmpty() {
            return "";
        }

        @Override
        protected void syncSingleDelta(String action, @Nullable String stage, long remainingTicks) {
            Set<String> permCopy;
            Map<String, Long> tempCopy;
            lock.lock();
            try {
                permCopy = Set.copyOf(serverBucket.permanent);
                tempCopy = Map.copyOf(serverBucket.temporary);
            } finally {
                lock.unlock();
            }
            if ("replace".equals(action)) {
                hooks.syncReplacement(null, permCopy, tempCopy, List.of());
            } else {
                hooks.syncServerDelta(action, stage, remainingTicks, permCopy, tempCopy);
            }
        }

        @Override
        public boolean sync() {
            rejectCandidateMutation();
            if (!hooks.hasSyncRecipient(null)) return false;
            syncSingleDelta("replace", "", 0L);
            return true;
        }
    }

    private final class PlayerStageHandle extends BaseStageHandle {
        private final PlayerStageBucket playerBucket;

        PlayerStageHandle(PlayerStageBucket playerBucket) {
            this.playerBucket = playerBucket;
        }

        @Override
        protected StageBucket bucket() {
            return playerBucket;
        }

        @Override
        protected String scopeName() {
            return "player";
        }

        @Override
        protected String playerUuidOrNull() {
            return playerBucket.uuid;
        }

        @Override
        protected String playerNameOrEmpty() {
            return resolvePlayerName(playerBucket.uuid, playerBucket.lastKnownName);
        }

        @Override
        protected void syncSingleDelta(String action, @Nullable String stage, long remainingTicks) {
            hooks.syncPlayerDelta(playerBucket.uuid, action, stage, remainingTicks);
        }

        @Override
        public boolean sync() {
            rejectCandidateMutation();
            if (!hooks.hasSyncRecipient(playerBucket.uuid)) return false;
            Set<String> playerPerm;
            Map<String, Long> playerTemp;
            Set<String> serverPerm;
            Map<String, Long> serverTemp;
            lock.lock();
            try {
                playerPerm = Set.copyOf(playerBucket.permanent);
                playerTemp = Map.copyOf(playerBucket.temporary);
                serverPerm = Set.copyOf(serverBucket.permanent);
                serverTemp = Map.copyOf(serverBucket.temporary);
            } finally {
                lock.unlock();
            }
            hooks.resyncFullToPlayer(playerBucket.uuid, playerPerm, playerTemp, serverPerm, serverTemp);
            return true;
        }
    }

    private record RemovedSnapshot(String stage, boolean temporary) {
    }

    private record PendingTickCallback(Consumer<StageTickContext> callback, StageTickContext context) {
    }

    private record ExpiredServerEntry(
            String stage,
            Set<String> fullServerPerm,
            Map<String, Long> fullServerTemp) {
    }

    private record ExpiredPlayerEntry(String uuid, String playerName, String stage) {
    }
}
