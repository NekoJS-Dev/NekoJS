package com.tkisor.nekojs.core.pack.sync;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.pack.ScriptPackRegistry;
import com.tkisor.nekojs.core.pack.ScriptPack;
import com.tkisor.nekojs.core.module.NekoTrustContext;
import com.tkisor.nekojs.core.lifecycle.NekoRuntimeRoot;

import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Client-side pack distribution pipeline, independent of the platform: receives the hash manifest
 * and bundle, saves them to the cache with traversal protection, rescans the disk and recomputes the
 * hash against the expected values, verifies the signature (Ed25519 with keyId pinning), decides
 * trust, then activates the SERVER_CACHE packs and triggers a CLIENT script reload. If trust fails
 * or the pack is rejected, it returns the disconnect message (v1 UX: disconnect and tell the player
 * to run {@code /nekojs trust <address>} and reconnect).
 *
 * <p>Threading: platform handlers receive packets on the network thread and switch to the main
 * thread through {@code enqueueWork} to run the methods here. The NeoForge configuration-phase
 * handler blocks the network thread with {@link #prepareMainThreadWork},
 * {@link #completeMainThreadWork} and {@link #awaitMainThreadWork} until the main thread finishes,
 * so the following registry sync happens after the remote scripts have run. Loaders without a
 * configuration phase run this after login and do not block.
 *
 * <p>The client's own engine.toml: mode=off ignores every synced pack; mode=hashOnly clears the
 * cache and never runs remote packs (single player and local memory connections skip sync entirely
 * at the platform entry point).
 */
public final class PackSyncClient {

    /** Bundle size limits, so a malicious oversized pack cannot exhaust memory or disk. */
    public static final int MAX_PACKS_PER_BUNDLE = 64;
    public static final int MAX_FILES_PER_PACK = 4096;
    public static final int MAX_FILE_BYTES = 8 * 1024 * 1024;
    public static final int MAX_MANIFEST_BYTES = 256 * 1024;
    public static final long MAX_TOTAL_BUNDLE_BYTES = 64L * 1024 * 1024;

    /**
     * Limit on how long to wait for main-thread work, so a deadlocked main thread cannot hang the
     * network thread forever.
     */
    private static final long MAIN_THREAD_WAIT_SECONDS = 30;

    /** CLIENT script reload hook installed by the platform: a guarded root.reload(CLIENT) wrapper from the loader entry. */
    private static volatile BooleanSupplier clientReloadHook;

    private static volatile CountDownLatch mainThreadLatch;

    /**
     * Sync session state for the current connection, shared by the hash manifest handler and the bundle
     * handler.
     */
    private static String activeBucket;
    private static String activeAddress;
    private static Map<String, String> expectedHashes = Map.of();
    /** Previous active state retained while a same-bucket replacement bundle is pending. */
    private static ActiveState pendingRollbackState;

    private PackSyncClient() {}

    public static void installClientReloadHook(BooleanSupplier hook) {
        clientReloadHook = hook;
    }

    /** Called on the network thread: registers a latch that the main-thread task completes through
     * {@link #completeMainThreadWork}. */
    public static void prepareMainThreadWork() {
        mainThreadLatch = new CountDownLatch(1);
    }

    /** Called in the finally block of the main-thread task. */
    public static void completeMainThreadWork() {
        CountDownLatch latch = mainThreadLatch;
        if (latch != null) latch.countDown();
    }

    /** Called on the network thread: blocks until the main-thread task finishes, or logs a WARN and
     * continues when the wait times out. */
    public static void awaitMainThreadWork() {
        CountDownLatch latch = mainThreadLatch;
        if (latch == null) return;
        try {
            if (!latch.await(MAIN_THREAD_WAIT_SECONDS, TimeUnit.SECONDS)) {
                NekoJS.LOGGER.warn("Pack sync main-thread work did not complete within {}s, continuing", MAIN_THREAD_WAIT_SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            mainThreadLatch = null;
        }
    }

    /* ================= Hash list ================= */

    /**
     * Handles a hash list on the main thread. An empty list clears the remote packs and reloads;
     * the hashOnly client mode clears them in the same way and never executes them. A non-empty
     * list records the expected hashes and waits for the bundle that follows.
     */
    public static synchronized Outcome handleHashList(NekoRuntimeRoot runtimeRoot,
                                                      String serverAddress, List<HashEntry> entries) {
        ActiveState previousState = pendingRollbackState != null ? pendingRollbackState : activeState();
        if (clientModeOff()) {
            if (!ScriptPackRegistry.get().serverCachePacks().isEmpty() || !expectedHashes.isEmpty()) {
                Outcome outcome = deactivateAndReload("client packSync mode is off", previousState, runtimeRoot, true);
                if (outcome.shouldDisconnect()) {
                    restoreConnectionState(previousState);
                    return outcome;
                }
            }
            activeAddress = null;
            activeBucket = null;
            expectedHashes = Map.of();
            pendingRollbackState = null;
            return Outcome.accepted();
        }
        String nextAddress = normalizeAddress(serverAddress);
        String nextBucket = PackSyncTrustStore.bucketFor(nextAddress);
        Map<String, String> hashes = new LinkedHashMap<>();
        for (HashEntry entry : entries) {
            hashes.put(entry.syncId(), entry.hash());
        }
        boolean connectionChanged = !nextAddress.equals(activeAddress) || !nextBucket.equals(activeBucket);
        Map<String, String> priorExpected = pendingRollbackState != null
                ? pendingRollbackState.expectedHashes() : expectedHashes;
        boolean activeSetChanged = !priorExpected.equals(hashes);
        activeAddress = nextAddress;
        activeBucket = nextBucket;
        expectedHashes = hashes;
        boolean revoked = false;
        boolean sameBucketReplacement = !connectionChanged && activeSetChanged
                && !ScriptPackRegistry.get().serverCachePacks().isEmpty()
                && activePackDirectories().equals(hashes.keySet().stream()
                        .map(SyncedPack::encodeSyncId).collect(java.util.stream.Collectors.toSet()));
        pendingRollbackState = sameBucketReplacement ? previousState : null;
        if (connectionChanged || (!ScriptPackRegistry.get().serverCachePacks().isEmpty()
                && activeSetChanged && !sameBucketReplacement)) {
            // Do not let the previous active/credential set run while this server's bundle is pending.
            Outcome outcome = deactivateAndReload("server pack hash list changed", previousState, runtimeRoot, true);
            if (outcome.shouldDisconnect()) {
                restoreConnectionState(previousState);
                return outcome;
            }
            revoked = true;
            pendingRollbackState = null;
        }

        if (hashes.isEmpty()) {
            // The server has no synced packs: unload the cached remote packs (the cache files stay) and
            // reload the client scripts
            if (!revoked) {
                Outcome outcome = deactivateAndReload("server sent an empty pack hash list", previousState, runtimeRoot, true);
                if (outcome.shouldDisconnect()) {
                    restoreConnectionState(previousState);
                    return outcome;
                }
            }
            pendingRollbackState = null;
            return Outcome.accepted();
        }
        if (clientModeHashOnly()) {
            if (!revoked) {
                Outcome outcome = deactivateAndReload("client packSync mode is hashOnly", previousState, runtimeRoot, true);
                if (outcome.shouldDisconnect()) {
                    restoreConnectionState(previousState);
                    return outcome;
                }
            }
            pendingRollbackState = null;
        }
        // Not hashOnly: a replacement in the same bucket keeps the old active set until the bundle
        // transaction replaces it. If the new bundle fails while hashing, persisting, trusting or
        // reloading, the old physical directory and runtime can still be restored.
        return Outcome.accepted();
    }

    /* ================= bundle ================= */

    /**
     * Handles a bundle on the main thread: size check, write to disk, rescan and recompute the
     * hashes, verify signatures, decide trust, then activate, reload and pin the signing keys.
     * Returns an Outcome; when disconnect is non-null the platform disconnects and shows the
     * message.
     */
    public static synchronized Outcome handleBundle(NekoRuntimeRoot runtimeRoot, List<SyncedPack> packs) {
        if (clientModeOff()) {
            Outcome outcome = deactivateAndReload("client packSync mode is off", activeState(), runtimeRoot, true);
            if (!outcome.shouldDisconnect()) {
                activeAddress = null;
                activeBucket = null;
                expectedHashes = Map.of();
            }
            return outcome;
        }
        if (clientModeHashOnly()) {
            Outcome outcome = deactivateAndReload("client packSync mode is hashOnly", activeState(), runtimeRoot, true);
            NekoJS.LOGGER.info("Ignoring server pack bundle (client packSync mode is hashOnly)");
            return outcome;
        }
        if (activeBucket == null) {
            // No hash list seen (unexpected order): fall back to hashes computed in memory as the baseline
            // for the on-disk check
            activeAddress = "unknown";
            activeBucket = PackSyncTrustStore.bucketFor(activeAddress);
            Map<String, String> hashes = new LinkedHashMap<>();
            for (SyncedPack pack : packs) {
                hashes.put(pack.syncId(), PackHasher.hash(
                    pack.manifestJson().getBytes(java.nio.charset.StandardCharsets.UTF_8), pack.files()));
            }
            expectedHashes = hashes;
        }

        ActiveState previousState = pendingRollbackState != null ? pendingRollbackState : activeState();
        String reject = validateBounds(packs);
        if (reject != null) {
            restoreConnectionState(previousState);
            return Outcome.disconnect(reject);
        }
        PackSyncTrustStore trustStore = PackSyncTrustStore.get();
        byte[] trustStoreSnapshot;
        try {
            trustStoreSnapshot = trustStore.snapshotBytes();
        } catch (IOException failure) {
            restoreConnectionState(previousState);
            return Outcome.disconnect("NekoJS remote script pack rejected: trust state snapshot failed");
        }
        Path bucketDir = ServerPackCache.bucketDir(activeBucket);
        Path stagingRoot = null;
        ServerPackCache.PhysicalReplacement replacement = null;
        boolean rolledBack = false;
        boolean committed = false;
        boolean clientReloadSucceeded = false;

        try {
            // 1) Per-pack validation: the manifest must parse as a JSON object (the activation
            //    scan would otherwise silently skip the pack — reject here with the reason in
            //    the trust result instead of accepting a pack that never activates), then verify
            //    the signature (unsigned packs are governed by allowUnsigned). The old active
            //    directory is still untouched at this point.
            for (SyncedPack pack : packs) {
                if (!expectedHashes.containsKey(pack.syncId())) {
                    NekoJS.LOGGER.warn("Ignoring unexpected server pack {}", pack.syncId());
                    continue;
                }
                if (!isJsonObjectManifest(pack.manifestJson())) {
                    restoreConnectionState(previousState);
                    return Outcome.disconnect("NekoJS remote script pack rejected (" + pack.syncId()
                        + "): manifest is not a JSON object");
                }
                PackSignatureVerifier.Result result = PackSignatureVerifier.verify(
                    pack.syncId(), pack.scopeName(), pack.manifestJson(), pack.files(),
                    allowUnsigned(), trustStore);
                if (!result.valid()) {
                    restoreConnectionState(previousState);
                    return Outcome.disconnect("NekoJS remote script pack rejected (" + pack.syncId()
                        + "): " + result.reason());
                }
            }

            // 2) Write into a staging directory in the same bucket. The old active directory only moves
            //    to the rollback directory when the runtime replacement really starts, so a hash
            //    or persist failure has no destructive effect.
            stagingRoot = ServerPackCache.createStagingRoot(bucketDir);
            for (SyncedPack pack : packs) {
                if (!expectedHashes.containsKey(pack.syncId())) continue;
                ServerPackCache.stagePack(stagingRoot, pack.syncId(), pack.manifestJson(), pack.files());
            }

            // 3) Rescan the staging directory and recompute the hashes to compare against the expected ones.
            Map<String, ServerPackCache.CachedPack> resolved = new LinkedHashMap<>();
            for (Map.Entry<String, String> expected : expectedHashes.entrySet()) {
                ServerPackCache.CachedPack cached = ServerPackCache.loadPack(stagingRoot, expected.getKey());
                if (cached == null) {
                    restoreConnectionState(previousState);
                    return Outcome.disconnect("NekoJS remote script pack integrity check failed: "
                        + expected.getKey() + " missing from staged cache");
                }
                if (!expected.getValue().equals(cached.hash())) {
                    restoreConnectionState(previousState);
                    return Outcome.disconnect("NekoJS remote script pack integrity check failed: "
                        + expected.getKey() + " hash mismatch after write");
                }
                var descriptor = com.tkisor.nekojs.core.pack.ScriptPackManifest.parse(
                        JsonParser.parseString(cached.manifestJson()).getAsJsonObject(), expected.getKey()).descriptor();
                if (descriptor != null) {
                    var manifest = JsonParser.parseString(cached.manifestJson()).getAsJsonObject();
                    String id = manifest.get("id").getAsString();
                    if (!expected.getKey().equals("packs:" + id) && !expected.getKey().equals("worldpacks:" + id)) {
                        throw new IllegalArgumentException("[NEKO-1016] Versioned remote package identity differs from its sync identity: " + expected.getKey());
                    }
                    descriptor.validateFiles(stagingRoot.resolve(SyncedPack.encodeSyncId(expected.getKey())),
                            com.tkisor.nekojs.core.ScriptFilePolicy.legacyRuntime());
                }
                resolved.put(expected.getKey(), cached);
            }

            if (resolved.isEmpty()) {
                // A bundle arrived with an empty expected list (every pack unexpected): log it, do not disconnect
                NekoJS.LOGGER.warn("Server pack bundle contained no expected packs; nothing to activate");
                return Outcome.accepted();
            }

            // 4) The trust decision and the remote key evidence are both settled before the physical
            // replacement.
            if (!trustStore.isServerTrusted(activeBucket)) {
                restoreConnectionState(previousState);
                return Outcome.disconnect(untrustedMessage(activeAddress));
            }
            if (runtimeRoot == null) {
                restoreConnectionState(previousState);
                return Outcome.disconnect("NekoJS remote script pack rejected: runtime trust owner is unavailable");
            }
            if (remoteSources(stagingRoot, resolved) == null) {
                restoreConnectionState(previousState);
                return Outcome.disconnect("NekoJS remote script pack rejected: explicit signing key evidence is required");
            }

            Path remoteRoot = bucketDir;
            List<ScriptPack> previousActive = previousState.activePacks();
            Path previousActiveRoot = previousState.activeRoot();

            // 5) Atomically replace staged directories while retaining old physical files.
            replacement = ServerPackCache.replaceStaged(stagingRoot, bucketDir, resolved.keySet());
            List<NekoTrustContext.RemoteSource> currentSources = remoteSources(bucketDir, resolved);
            if (currentSources == null) {
                RollbackOutcome rollback = rollbackBundle(runtimeRoot, remoteRoot, replacement, trustStore, trustStoreSnapshot,
                        previousActive, previousActiveRoot, previousState);
                rolledBack = true;
                return Outcome.disconnect(rollback.decorate(
                        "NekoJS remote script pack rejected: explicit signing key evidence is required"));
            }

            // 6) Activate + authorize + reload. Any failure restores old files first, then
            //    rebuilds the old registry/credentials against those restored paths.
            try {
                if (previousActiveRoot == null || !previousActiveRoot.equals(remoteRoot)) runtimeRoot.revokeRemoteSources(remoteRoot);
            } catch (Exception failure) {
                RollbackOutcome rollback = rollbackBundle(runtimeRoot, remoteRoot, replacement, trustStore, trustStoreSnapshot,
                        previousActive, previousActiveRoot, previousState);
                rolledBack = true;
                return Outcome.disconnect(rollback.decorate(
                        "NekoJS remote script pack rejected: runtime trust reset failed"));
            }
            ScriptPackRegistry.get().activateServerCachePacks(bucketDir, resolved.keySet());
            Map<String, String> scopeNames = new LinkedHashMap<>();
            for (SyncedPack pack : packs) scopeNames.put(pack.syncId(), pack.scopeName());
            for (Map.Entry<String, ServerPackCache.CachedPack> entry : resolved.entrySet()) {
                pinSigningKey(trustStore, entry.getKey(), scopeNames.get(entry.getKey()), entry.getValue());
            }
            try {
                runtimeRoot.authorizeRemoteSources(currentSources, remoteRoot);
            } catch (Exception failure) {
                RollbackOutcome rollback = rollbackBundle(runtimeRoot, remoteRoot, replacement, trustStore, trustStoreSnapshot,
                        previousActive, previousActiveRoot, previousState);
                rolledBack = true;
                return Outcome.disconnect(rollback.decorate(
                        "NekoJS remote script pack rejected: runtime authorization failed"));
            }
            if (!reloadClientScripts("server pack bundle applied", true)) {
                RollbackOutcome rollback = rollbackBundle(runtimeRoot, remoteRoot, replacement, trustStore, trustStoreSnapshot,
                        previousActive, previousActiveRoot, previousState);
                rolledBack = true;
                return Outcome.disconnect(rollback.decorate(
                        "NekoJS remote script pack rejected: CLIENT script reload failed"));
            }
            clientReloadSucceeded = true;
            replacement.commit();
            committed = true;
            pendingRollbackState = null;
            NekoJS.LOGGER.info("Activated {} remote script pack(s) from server {}", resolved.size(), activeAddress);
            return Outcome.accepted();
        } catch (Throwable failure) {
            ServerPackCache.PhysicalReplacement failedReplacement = replacement;
            if (failedReplacement == null && failure instanceof ServerPackCache.ReplacementFailure replacementFailure) {
                failedReplacement = replacementFailure.replacement();
                replacement = failedReplacement;
            }
            if (failedReplacement != null && !rolledBack && !committed) {
                RollbackOutcome rollback = rollbackBundle(runtimeRoot, bucketDir, failedReplacement,
                        trustStore, trustStoreSnapshot,
                        previousState.activePacks(), previousState.activeRoot(), previousState,
                        clientReloadSucceeded);
                rolledBack = true;
                return Outcome.disconnect(rollback.decorate(
                        "NekoJS remote script pack rejected: " + failure.getMessage()));
            } else {
                restoreConnectionState(previousState);
            }
            return Outcome.disconnect("NekoJS remote script pack rejected: " + failure.getMessage());
        } finally {
            if (!committed && !rolledBack && stagingRoot != null) {
                ServerPackCache.deleteRecursively(stagingRoot);
            }
        }
    }

    /* ================= Disconnect ================= */

    /** Disconnect or leaving the world: unloads the SERVER_CACHE packs (the cache files stay).
     * Reloads when a CLIENT runtime is active. */
    public static synchronized Outcome handleDisconnect(NekoRuntimeRoot runtimeRoot) {
        ActiveState previousState = activeState();
        pendingRollbackState = null;
        expectedHashes = Map.of();
        activeAddress = null;
        activeBucket = null;
        boolean reloadRequired = hasActiveClientRuntime(runtimeRoot);
        Outcome outcome = deactivateAndReload("disconnected from server", previousState, runtimeRoot, reloadRequired);
        if (outcome.shouldDisconnect()) {
            restoreConnectionState(previousState);
        }
        return outcome;
    }

    /* ================= Internals ================= */

    private static Outcome deactivateAndReload(String reason, ActiveState previousState,
                                               NekoRuntimeRoot runtimeRoot, boolean reloadRequired) {
        var removed = ScriptPackRegistry.get().deactivateServerCachePacks();
        Path remoteRoot = previousState.remoteRoot();
        if (runtimeRoot != null) {
            try {
                runtimeRoot.revokeRemoteSources(remoteRoot);
            } catch (Throwable failure) {
                restorePreviousActivation(runtimeRoot, remoteRoot, previousState.activePacks(), previousState.activeRoot());
                return Outcome.disconnect("NekoJS remote script pack deactivation failed: " + failure.getMessage());
            }
        }
        if (!removed.isEmpty()) {
            NekoJS.LOGGER.info("Deactivated {} server cache pack(s): {}", removed.size(), reason);
            if (!reloadClientScripts(reason, reloadRequired)) {
                NekoJS.LOGGER.error("CLIENT runtime did not accept server pack deactivation ({})", reason);
                restorePreviousActivation(runtimeRoot, remoteRoot, previousState.activePacks(), previousState.activeRoot());
                return Outcome.disconnect("NekoJS remote script pack deactivation rejected: CLIENT script reload failed");
            }
        }
        return Outcome.accepted();
    }

    private static ActiveState activeState() {
        List<ScriptPack> active = List.copyOf(ScriptPackRegistry.get().serverCachePacks());
        Path activeRoot = active.isEmpty() ? null : active.get(0).root().getParent();
        return new ActiveState(activeAddress, activeBucket, expectedHashes, active, activeRoot,
                activeRemoteRoot());
    }

    private static void restoreConnectionState(ActiveState state) {
        pendingRollbackState = null;
        activeAddress = state.address();
        activeBucket = state.bucket();
        expectedHashes = state.expectedHashes();
    }

    /** Keep the old active generation and its trust set when a replacement reload is rejected. */
    private static boolean restorePreviousActivation(NekoRuntimeRoot runtimeRoot, Path failedRoot,
                                                     List<ScriptPack> previous, Path previousRoot) {
        if (previous == null || previous.isEmpty()) {
            ScriptPackRegistry.get().deactivateServerCachePacks();
            if (runtimeRoot != null) {
                try {
                    runtimeRoot.revokeRemoteSources(failedRoot);
                } catch (Throwable failure) {
                    NekoJS.LOGGER.error("Failed to revoke remote sources while restoring an empty pack set", failure);
                    return false;
                }
            }
            return true;
        }
        Path root = previousRoot;
        if (root == null) {
            root = previous.get(0).root().getParent();
        }
        List<String> syncIds = previous.stream().map(PackSyncClient::syncIdOf).toList();
        try {
            ScriptPackRegistry.get().activateServerCachePacks(root, syncIds);
        } catch (Throwable failure) {
            NekoJS.LOGGER.error("Failed to restore the previous server pack registry", failure);
            return false;
        }
        if (runtimeRoot == null) return true;
        List<NekoTrustContext.RemoteSource> sources = remoteSources(previous);
        if (sources == null) {
            NekoJS.LOGGER.error("Failed to reconstruct the previous server pack trust set after CLIENT reload failure");
            return false;
        }
        try {
            runtimeRoot.authorizeRemoteSources(sources, root);
        } catch (Exception failure) {
            NekoJS.LOGGER.error("Failed to restore the previous server pack trust set after CLIENT reload failure", failure);
            return false;
        }
        return true;
    }

    /**
     * Roll back a bundle transaction in dependency order: physical files first, then registry
     * and runtime credentials, and finally the persisted key-pin changes and connection state.
     */
    private static RollbackOutcome rollbackBundle(NekoRuntimeRoot runtimeRoot, Path failedRoot,
                                                  ServerPackCache.PhysicalReplacement replacement,
                                                  PackSyncTrustStore trustStore, byte[] trustStoreSnapshot,
                                                  List<ScriptPack> previous, Path previousRoot,
                                                  ActiveState previousState) {
        return rollbackBundle(runtimeRoot, failedRoot, replacement, trustStore, trustStoreSnapshot,
                previous, previousRoot, previousState, false);
    }

    /**
     * Roll back physical/logical state and, when the new client generation was already exposed,
     * reload the restored old files before reporting failure. A failed compensation is fatal:
     * reporting a normal rollback in that state would leave the client executing a generation
     * that no longer matches the physical cache.
     */
    private static RollbackOutcome rollbackBundle(NekoRuntimeRoot runtimeRoot, Path failedRoot,
                                                  ServerPackCache.PhysicalReplacement replacement,
                                                  PackSyncTrustStore trustStore, byte[] trustStoreSnapshot,
                                                  List<ScriptPack> previous, Path previousRoot,
                                                  ActiveState previousState,
                                                  boolean compensateClientReload) {
        try {
            replacement.restore();
        } catch (IOException failure) {
            NekoJS.LOGGER.error("Failed to restore previous server pack files after replacement failure", failure);
            return failClosedAfterRollbackFailure(runtimeRoot, failedRoot, trustStore, trustStoreSnapshot,
                    "physical server pack restore failed: " + failure.getMessage());
        }
        try {
            if (!restorePreviousActivation(runtimeRoot, failedRoot, previous, previousRoot)) {
                return failClosedAfterRollbackFailure(runtimeRoot, failedRoot, trustStore, trustStoreSnapshot,
                        "logical server pack restore failed");
            }
        } catch (Throwable failure) {
            NekoJS.LOGGER.error("Failed to restore previous server pack activation after replacement failure", failure);
            return failClosedAfterRollbackFailure(runtimeRoot, failedRoot, trustStore, trustStoreSnapshot,
                    "logical server pack restore failed: " + failure.getMessage());
        }
        if (compensateClientReload && !reloadClientScripts("compensating reload of previous server pack generation", true)) {
            return failClosedAfterRollbackFailure(runtimeRoot, failedRoot, trustStore, trustStoreSnapshot,
                    "compensating CLIENT reload failed");
        }
        try {
            trustStore.restoreBytes(trustStoreSnapshot);
        } catch (IOException failure) {
            NekoJS.LOGGER.error("Failed to restore pack sync trust state after replacement failure", failure);
            return failClosedAfterRollbackFailure(runtimeRoot, failedRoot, trustStore, trustStoreSnapshot,
                    "trust state restore failed: " + failure.getMessage());
        }
        restoreConnectionState(previousState);
        return RollbackOutcome.success();
    }

    /** Do not reinstall logical state against an unknown physical directory after rollback fails. */
    private static RollbackOutcome failClosedAfterRollbackFailure(NekoRuntimeRoot runtimeRoot,
                                                                   Path failedRoot,
                                                                   PackSyncTrustStore trustStore,
                                                                   byte[] trustStoreSnapshot,
                                                                   String reason) {
        ScriptPackRegistry.get().deactivateServerCachePacks();
        try {
            if (runtimeRoot != null) runtimeRoot.revokeRemoteSources(failedRoot);
        } catch (Throwable failure) {
            NekoJS.LOGGER.error("Failed to revoke remote sources after fatal pack rollback failure", failure);
        }
        try {
            trustStore.restoreBytes(trustStoreSnapshot);
        } catch (IOException failure) {
            NekoJS.LOGGER.error("Failed to restore pack sync trust state after fatal rollback failure", failure);
            reason += "; trust state restore failed: " + failure.getMessage();
        }
        activeAddress = null;
        activeBucket = null;
        expectedHashes = Map.of();
        pendingRollbackState = null;
        return RollbackOutcome.fatal(reason);
    }

    private static String syncIdOf(ScriptPack pack) {
        String encoded = pack.root().getFileName().toString();
        int separator = encoded.indexOf('_');
        return separator > 0
                ? encoded.substring(0, separator) + ":" + encoded.substring(separator + 1)
                : encoded;
    }

    private static java.util.Set<String> activePackDirectories() {
        java.util.Set<String> directories = new java.util.HashSet<>();
        for (ScriptPack pack : ScriptPackRegistry.get().serverCachePacks()) {
            Path root = pack.root();
            if (root != null && root.getFileName() != null) {
                directories.add(root.getFileName().toString());
            }
        }
        return directories;
    }

    private static List<NekoTrustContext.RemoteSource> remoteSources(List<ScriptPack> packs) {
        List<NekoTrustContext.RemoteSource> sources = new ArrayList<>();
        for (ScriptPack pack : packs) {
            JsonObject signature = pack.manifest() == null ? null : pack.manifest().signature();
            String keyId = signature == null ? null : PackSignatureVerifier.string(signature, "keyId");
            if ((keyId == null || keyId.isBlank()) && !allowUnsigned()) {
                return null;
            }
            try (var files = Files.walk(pack.root())) {
                files.filter(Files::isRegularFile)
                        .filter(file -> !file.getFileName().toString().equals("manifest.json"))
                        .forEach(file -> sources.add(keyId == null || keyId.isBlank()
                                ? NekoTrustContext.RemoteSource.unsigned(file, syncIdOf(pack))
                                : new NekoTrustContext.RemoteSource(file, syncIdOf(pack), keyId)));
            } catch (IOException failure) {
                return null;
            }
        }
        return List.copyOf(sources);
    }

    private static boolean reloadClientScripts(String reason, boolean required) {
        BooleanSupplier hook = clientReloadHook;
        if (hook == null) {
            if (!required) {
                NekoJS.LOGGER.debug("No client reload hook installed; no active runtime requires reload ({})", reason);
                return true;
            }
            NekoJS.LOGGER.error("No client reload hook installed; rejecting CLIENT reload ({})", reason);
            return false;
        }
        try {
            return hook.getAsBoolean();
        } catch (Throwable e) {
            NekoJS.LOGGER.error("CLIENT script reload after pack sync failed", e);
            return false;
        }
    }

    private static boolean hasActiveClientRuntime(NekoRuntimeRoot runtimeRoot) {
        return runtimeRoot != null
                && runtimeRoot.scriptManagerOrNull(com.tkisor.nekojs.api.ScriptType.CLIENT) != null;
    }

    private static Path activeRemoteRoot() {
        return activeBucket == null ? null : ServerPackCache.bucketDir(activeBucket);
    }

    private static void pinSigningKey(
        PackSyncTrustStore trustStore, String syncId, String scopeName, ServerPackCache.CachedPack cached
    ) {
        com.google.gson.JsonObject signature = PackSignatureVerifier.parseSignatureBlock(cached.manifestJson());
        if (signature == null) return;
        String keyId = PackSignatureVerifier.string(signature, "keyId");
        String publicKey = PackSignatureVerifier.string(signature, "publicKey");
        if (keyId == null || publicKey == null || scopeName == null) return;
        PackSignatureVerifier.Result verified = PackSignatureVerifier.verify(
            syncId, scopeName, cached.manifestJson(), cached.files(), true, trustStore);
        if (!verified.valid() || verified.fingerprint() == null) {
            throw new IllegalStateException("[NEKO-1017] Signing key admission failed after cache validation; pack="
                    + syncId + "; reason=" + verified.reason());
        }
        trustStore.trustPublicKey(keyId, publicKey, verified.fingerprint(), activeAddress == null ? "unknown" : activeAddress);
        if (!publicKey.equals(trustStore.trustedPublicKey(keyId))) {
            throw new IllegalStateException("[NEKO-1017] Signing key persistence failed; pack=" + syncId + "; key=" + keyId);
        }
    }

    private static List<NekoTrustContext.RemoteSource> remoteSources(
            Path bucketDir, Map<String, ServerPackCache.CachedPack> resolved) {
        List<NekoTrustContext.RemoteSource> sources = new ArrayList<>();
        for (Map.Entry<String, ServerPackCache.CachedPack> entry : resolved.entrySet()) {
            JsonObject signature = PackSignatureVerifier.parseSignatureBlock(entry.getValue().manifestJson());
            String keyId = signature == null ? null : PackSignatureVerifier.string(signature, "keyId");
            if ((keyId == null || keyId.isBlank()) && !allowUnsigned() && !entry.getValue().files().isEmpty()) return null;
            Path packDir = bucketDir.resolve(SyncedPack.encodeSyncId(entry.getKey()));
            for (PackContentFile file : entry.getValue().files()) {
                Path materialized = ServerPackCache.resolveInside(packDir, file.relativePath());
                if (materialized != null) {
                    sources.add(keyId == null || keyId.isBlank()
                            ? NekoTrustContext.RemoteSource.unsigned(materialized, entry.getKey())
                            : new NekoTrustContext.RemoteSource(materialized, entry.getKey(), keyId));
                }
            }
        }
        return Collections.unmodifiableList(sources);
    }

    private static String validateBounds(List<SyncedPack> packs) {
        if (packs.size() > MAX_PACKS_PER_BUNDLE) {
            return "NekoJS remote script bundle rejected: too many packs (" + packs.size() + ")";
        }
        long total = 0;
        for (SyncedPack pack : packs) {
            if (pack.manifestJson() == null
                || pack.manifestJson().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_MANIFEST_BYTES) {
                return "NekoJS remote script bundle rejected: manifest too large (" + pack.syncId() + ")";
            }
            if (pack.files().size() > MAX_FILES_PER_PACK) {
                return "NekoJS remote script bundle rejected: too many files (" + pack.syncId() + ")";
            }
            for (PackContentFile file : pack.files()) {
                if (file.bytes().length > MAX_FILE_BYTES) {
                    return "NekoJS remote script bundle rejected: file too large ("
                        + pack.syncId() + "/" + file.relativePath() + ")";
                }
                total += file.bytes().length;
            }
        }
        if (total > MAX_TOTAL_BUNDLE_BYTES) {
            return "NekoJS remote script bundle rejected: bundle too large (" + total + " bytes)";
        }
        return null;
    }

    private static String untrustedMessage(String address) {
        return "NekoJS: this server distributes signed script packs (" + address + ").\n"
            + "Run /nekojs trust " + address + " (in a singleplayer world or any server chat) and reconnect to allow them.";
    }

    private static String normalizeAddress(String address) {
        return address == null ? "unknown" : address.trim().toLowerCase();
    }

    /**
     * Remote socket address → the server address text used for bucket computation (shared by
     * both loader receive bridges). {@code Minecraft#getCurrentServer()} is not ready during
     * the configuration phase, so the hostname comes from the connection's remote address; the
     * normalized text is both the bucket input and the address players are told to re-type in
     * the untrusted disconnect hint.
     */
    public static String normalizeRemoteAddress(java.net.SocketAddress remote) {
        if (remote instanceof java.net.InetSocketAddress isa) {
            String host = isa.getHostString();
            if (host != null && !host.isBlank()) {
                // Default-locale lowercasing must match PackSyncTrustStore.bucketFor exactly;
                // Locale.ROOT here would compute different buckets on locale-sensitive JVMs
                // and silently orphan trust entries persisted by the previous resolver.
                return host.trim().toLowerCase();
            }
        }
        return remote != null ? remote.toString() : "unknown";
    }

    /** Manifest validity: parses as a JSON object (the same floor as local packs in {@code ScriptPackManifest.load}). */
    private static boolean isJsonObjectManifest(String manifestJson) {
        try {
            return JsonParser.parseString(manifestJson).isJsonObject();
        } catch (Exception malformed) {
            return false;
        }
    }

    private static boolean clientModeOff() {
        return !ClassFilter.INSTANCE.config().packSyncEnabled();
    }

    private static boolean clientModeHashOnly() {
        return SandboxConfig.PACK_SYNC_HASH_ONLY.equalsIgnoreCase(ClassFilter.INSTANCE.config().packSyncMode());
    }

    private static boolean allowUnsigned() {
        return ClassFilter.INSTANCE.config().packSyncAllowUnsigned();
    }

    /** One hash-list entry; platform payloads map to this type in common. */
    public record HashEntry(String syncId, String hash) {}

    private record ActiveState(String address, String bucket, Map<String, String> expectedHashes,
                               List<ScriptPack> activePacks, Path activeRoot, Path remoteRoot) {
        private ActiveState {
            expectedHashes = Map.copyOf(expectedHashes == null ? Map.of() : expectedHashes);
            activePacks = List.copyOf(activePacks == null ? List.of() : activePacks);
        }
    }

    private record RollbackOutcome(boolean restored, String fatalReason) {
        static RollbackOutcome success() {
            return new RollbackOutcome(true, null);
        }

        static RollbackOutcome fatal(String reason) {
            return new RollbackOutcome(false, reason);
        }

        String decorate(String baseMessage) {
            return fatalReason == null ? baseMessage : baseMessage + "; fatal rollback: " + fatalReason;
        }
    }

    /** Result of handling a bundle. When disconnect is non-null the platform disconnects and shows
     * the message. */
    public record Outcome(String disconnect) {

        static Outcome accepted() {
            return new Outcome(null);
        }

        static Outcome disconnect(String message) {
            return new Outcome(message);
        }

        public boolean shouldDisconnect() {
            return disconnect != null;
        }
    }
}
