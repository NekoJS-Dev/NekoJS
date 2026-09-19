package com.tkisor.nekojs.core.pack.sync;

import com.google.gson.JsonObject;
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
 * 包分发客户端管线（平台无关）：接收哈希清单与 bundle → 落盘缓存（路径穿越防护）→
 * 从盘重扫重算哈希对照预期 → 验签（Ed25519 + keyId pinning）→ 信任判定 → 激活
 * SERVER_CACHE 包并触发 CLIENT 脚本重载。未信任/被拒时返回断连消息（v1 UX：断连 +
 * 提示 {@code /nekojs trust <address>} 后重连）。
 *
 * <p>线程模型：平台处理器在网络线程收包，经 {@code enqueueWork} 切主线程执行本类方法；
 * NeoForge 配置阶段的处理器用 {@link #prepareMainThreadWork}/{@link #completeMainThreadWork}/
 * {@link #awaitMainThreadWork} 阻塞网络线程直到主线程执行完毕——保证后续注册表同步
 * 在远端脚本执行之后进行。无配置阶段的加载器改为登录后执行，无需阻塞。
 *
 * <p>客户端自身 engine.toml 语义：mode=off 忽略全部同步包；mode=hashOnly 清空并永不
 * 执行远端包（单人/本地内存连接由平台入口直接跳过同步）。
 */
public final class PackSyncClient {

    /** bundle 体量上限（防恶意巨型包撑爆内存/磁盘）。 */
    public static final int MAX_PACKS_PER_BUNDLE = 64;
    public static final int MAX_FILES_PER_PACK = 4096;
    public static final int MAX_FILE_BYTES = 8 * 1024 * 1024;
    public static final int MAX_MANIFEST_BYTES = 256 * 1024;
    public static final long MAX_TOTAL_BUNDLE_BYTES = 64L * 1024 * 1024;

    /** 主线程执行等待上限（避免主线程死锁时网络线程被永久挂起）。 */
    private static final long MAIN_THREAD_WAIT_SECONDS = 30;

    /** 平台安装的 CLIENT 脚本重载钩子（loader entry 注入的 root.reload(CLIENT) 守卫包装）。 */
    private static volatile BooleanSupplier clientReloadHook;

    private static volatile CountDownLatch mainThreadLatch;

    /** 当前连接的同步会话状态（哈希清单与 bundle 处理器共享）。 */
    private static String activeBucket;
    private static String activeAddress;
    private static Map<String, String> expectedHashes = Map.of();

    private PackSyncClient() {}

    public static void installClientReloadHook(BooleanSupplier hook) {
        clientReloadHook = hook;
    }

    /** 网络线程调用：登记一个 latch，主线程任务完成后 {@link #completeMainThreadWork}。 */
    public static void prepareMainThreadWork() {
        mainThreadLatch = new CountDownLatch(1);
    }

    /** 主线程任务 finally 中调用。 */
    public static void completeMainThreadWork() {
        CountDownLatch latch = mainThreadLatch;
        if (latch != null) latch.countDown();
    }

    /** 网络线程调用：阻塞至主线程任务完成（超时放行并 WARN）。 */
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

    /* ================= 哈希清单 ================= */

    /**
     * 处理哈希清单（主线程）。空清单 = 清空远端包并重载；hashOnly 客户端模式同样清空
     * 且永不执行。非空清单记录预期哈希，等待随后的 bundle。
     */
    public static synchronized Outcome handleHashList(NekoRuntimeRoot runtimeRoot,
                                                      String serverAddress, List<HashEntry> entries) {
        ActiveState previousState = activeState();
        if (clientModeOff()) {
            if (!ScriptPackRegistry.get().serverCachePacks().isEmpty() || !expectedHashes.isEmpty()) {
                Outcome outcome = deactivateAndReload("client packSync mode is off", previousState, runtimeRoot);
                if (outcome.shouldDisconnect()) {
                    restoreConnectionState(previousState);
                    return outcome;
                }
            }
            activeAddress = null;
            activeBucket = null;
            expectedHashes = Map.of();
            return Outcome.accepted();
        }
        String nextAddress = normalizeAddress(serverAddress);
        String nextBucket = PackSyncTrustStore.bucketFor(nextAddress);
        Map<String, String> hashes = new LinkedHashMap<>();
        for (HashEntry entry : entries) {
            hashes.put(entry.syncId(), entry.hash());
        }
        boolean connectionChanged = !nextAddress.equals(activeAddress) || !nextBucket.equals(activeBucket);
        boolean activeSetChanged = !expectedHashes.equals(hashes);
        activeAddress = nextAddress;
        activeBucket = nextBucket;
        expectedHashes = hashes;
        boolean revoked = false;
        if (connectionChanged || (!ScriptPackRegistry.get().serverCachePacks().isEmpty() && activeSetChanged)) {
            // Do not let the previous active/credential set run while this server's bundle is pending.
            Outcome outcome = deactivateAndReload("server pack hash list changed", previousState, runtimeRoot);
            if (outcome.shouldDisconnect()) {
                restoreConnectionState(previousState);
                return outcome;
            }
            revoked = true;
        }

        if (hashes.isEmpty()) {
            // 服务器无同步包：清空远端缓存包（缓存文件保留）并重载客户端脚本
            if (!revoked) {
                Outcome outcome = deactivateAndReload("server sent an empty pack hash list", previousState, runtimeRoot);
                if (outcome.shouldDisconnect()) {
                    restoreConnectionState(previousState);
                    return outcome;
                }
            }
            return Outcome.accepted();
        }
        if (clientModeHashOnly()) {
            if (!revoked) {
                Outcome outcome = deactivateAndReload("client packSync mode is hashOnly", previousState, runtimeRoot);
                if (outcome.shouldDisconnect()) {
                    restoreConnectionState(previousState);
                    return outcome;
                }
            }
        }
        // 非 hashOnly：保留当前激活集，等待 bundle 到达后整体替换（避免双重重载）
        return Outcome.accepted();
    }

    /* ================= bundle ================= */

    /**
     * 处理 bundle（主线程）：校验体量 → 落盘 → 重扫重哈希对照 → 验签 → 信任判定 →
     * 激活 + 重载 + pinning 签名公钥。返回 Outcome：disconnect 非空时平台应断连并展示消息。
     */
    public static synchronized Outcome handleBundle(NekoRuntimeRoot runtimeRoot, List<SyncedPack> packs) {
        if (clientModeOff()) {
            Outcome outcome = deactivateAndReload("client packSync mode is off", activeState(), runtimeRoot);
            if (!outcome.shouldDisconnect()) {
                activeAddress = null;
                activeBucket = null;
                expectedHashes = Map.of();
            }
            return outcome;
        }
        if (clientModeHashOnly()) {
            Outcome outcome = deactivateAndReload("client packSync mode is hashOnly", activeState(), runtimeRoot);
            NekoJS.LOGGER.info("Ignoring server pack bundle (client packSync mode is hashOnly)");
            return outcome;
        }
        if (activeBucket == null) {
            // 未见过哈希清单（异常顺序）：以内存侧自算哈希做落盘自检基准
            activeAddress = "unknown";
            activeBucket = PackSyncTrustStore.bucketFor(activeAddress);
            Map<String, String> hashes = new LinkedHashMap<>();
            for (SyncedPack pack : packs) {
                hashes.put(pack.syncId(), PackHasher.hash(
                    pack.manifestJson().getBytes(java.nio.charset.StandardCharsets.UTF_8), pack.files()));
            }
            expectedHashes = hashes;
        }

        String reject = validateBounds(packs);
        if (reject != null) return Outcome.disconnect(reject);

        Path bucketDir = ServerPackCache.bucketDir(activeBucket);

        // 1) 逐包验签（未签名受 allowUnsigned 控制）——签名块随 manifest 原文一并落盘，
        //    重扫后再次以盘上内容为准（下面第 3 步）。
        for (SyncedPack pack : packs) {
            if (!expectedHashes.containsKey(pack.syncId())) {
                NekoJS.LOGGER.warn("Ignoring unexpected server pack {}", pack.syncId());
                continue;
            }
            PackSignatureVerifier.Result result = PackSignatureVerifier.verify(
                pack.syncId(), pack.scopeName(), pack.manifestJson(), pack.files(),
                allowUnsigned(), PackSyncTrustStore.get());
            if (!result.valid()) {
                return Outcome.disconnect("NekoJS remote script pack rejected (" + pack.syncId()
                    + "): " + result.reason());
            }
        }

        // 2) 落盘（删旧目录重建 + 路径穿越校验）
        for (SyncedPack pack : packs) {
            if (!expectedHashes.containsKey(pack.syncId())) continue;
            ServerPackCache.persistPack(bucketDir, pack.syncId(), pack.manifestJson(), pack.files());
        }

        // 3) 从盘重扫 + 重算哈希对照预期（写盘完整性自检）
        Map<String, ServerPackCache.CachedPack> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, String> expected : expectedHashes.entrySet()) {
            ServerPackCache.CachedPack cached = ServerPackCache.loadPack(bucketDir, expected.getKey());
            if (cached == null) {
                return Outcome.disconnect("NekoJS remote script pack integrity check failed: "
                    + expected.getKey() + " missing from disk cache");
            }
            if (!expected.getValue().equals(cached.hash())) {
                return Outcome.disconnect("NekoJS remote script pack integrity check failed: "
                    + expected.getKey() + " hash mismatch after write");
            }
            resolved.put(expected.getKey(), cached);
        }

        if (resolved.isEmpty()) {
            // 预期清单为空却推了 bundle（全部为意外包）——只记录，不断连
            NekoJS.LOGGER.warn("Server pack bundle contained no expected packs; nothing to activate");
            return Outcome.accepted();
        }

        // 4) 信任判定：bucket 未信任 → 断连 + 提示 /nekojs trust
        PackSyncTrustStore trustStore = PackSyncTrustStore.get();
        if (!trustStore.isServerTrusted(activeBucket)) {
            return Outcome.disconnect(untrustedMessage(activeAddress));
        }

        if (runtimeRoot == null) {
            return Outcome.disconnect("NekoJS remote script pack rejected: runtime trust owner is unavailable");
        }
        List<NekoTrustContext.RemoteSource> remoteSources = remoteSources(bucketDir, resolved);
        if (remoteSources == null) {
            return Outcome.disconnect("NekoJS remote script pack rejected: explicit signing key evidence is required");
        }
        Path remoteRoot = bucketDir;
        List<ScriptPack> previousActive = List.copyOf(ScriptPackRegistry.get().serverCachePacks());
        Path previousActiveRoot = previousActive.isEmpty() ? null
                : previousActive.get(0).root().getParent();
        // Replace the credential set before exposing the replacement pack to reload/execute.
        try {
            runtimeRoot.revokeRemoteSources(remoteRoot);
        } catch (Exception failure) {
            return Outcome.disconnect("NekoJS remote script pack rejected: runtime trust reset failed");
        }

        // 5) 激活 + 重载 + pinning 签名公钥（v1：信任服务器即信任其当前签名密钥；
        //    此后同 keyId 换钥会被验签拒绝）
        ScriptPackRegistry.get().activateServerCachePacks(bucketDir, resolved.keySet());
        Map<String, String> scopeNames = new LinkedHashMap<>();
        for (SyncedPack pack : packs) scopeNames.put(pack.syncId(), pack.scopeName());
        for (Map.Entry<String, ServerPackCache.CachedPack> entry : resolved.entrySet()) {
            pinSigningKey(trustStore, entry.getKey(), scopeNames.get(entry.getKey()), entry.getValue());
        }
        try {
            runtimeRoot.authorizeRemoteSources(remoteSources, remoteRoot);
        } catch (Exception failure) {
            restorePreviousActivation(runtimeRoot, remoteRoot, previousActive, previousActiveRoot);
            return Outcome.disconnect("NekoJS remote script pack rejected: runtime authorization failed");
        }
        if (!reloadClientScripts("server pack bundle applied")) {
            // The registry and remote credentials were made visible before reload so the
            // candidate can resolve the bundle. A failed reload must remove that selection again;
            // leaving it active would make registry/trust state disagree with the live runtime.
            restorePreviousActivation(runtimeRoot, remoteRoot, previousActive, previousActiveRoot);
            return Outcome.disconnect("NekoJS remote script pack rejected: CLIENT script reload failed");
        }
        NekoJS.LOGGER.info("Activated {} remote script pack(s) from server {}", resolved.size(), activeAddress);
        return Outcome.accepted();
    }

    /* ================= 断线 ================= */

    /** 断线/离开世界：卸载 SERVER_CACHE 包（缓存文件保留）；有激活包时重载客户端脚本。 */
    public static synchronized void handleDisconnect(NekoRuntimeRoot runtimeRoot) {
        ActiveState previousState = activeState();
        expectedHashes = Map.of();
        activeAddress = null;
        activeBucket = null;
        Outcome outcome = deactivateAndReload("disconnected from server", previousState, runtimeRoot);
        if (outcome.shouldDisconnect()) {
            restoreConnectionState(previousState);
        }
    }

    /* ================= 内部 ================= */

    private static Outcome deactivateAndReload(String reason, ActiveState previousState,
                                               NekoRuntimeRoot runtimeRoot) {
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
            if (!reloadClientScripts(reason)) {
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
        activeAddress = state.address();
        activeBucket = state.bucket();
        expectedHashes = state.expectedHashes();
    }

    /** Keep the old active generation and its trust set when a replacement reload is rejected. */
    private static void restorePreviousActivation(NekoRuntimeRoot runtimeRoot, Path failedRoot,
                                                  List<ScriptPack> previous, Path previousRoot) {
        if (previous == null || previous.isEmpty()) {
            ScriptPackRegistry.get().deactivateServerCachePacks();
            if (runtimeRoot != null) {
                runtimeRoot.revokeRemoteSources(failedRoot);
            }
            return;
        }
        Path root = previousRoot;
        if (root == null) {
            root = previous.get(0).root().getParent();
        }
        List<String> syncIds = previous.stream().map(PackSyncClient::syncIdOf).toList();
        ScriptPackRegistry.get().activateServerCachePacks(root, syncIds);
        if (runtimeRoot == null) return;
        runtimeRoot.revokeRemoteSources(failedRoot);
        List<NekoTrustContext.RemoteSource> sources = remoteSources(previous);
        if (sources == null) {
            NekoJS.LOGGER.error("Failed to reconstruct the previous server pack trust set after CLIENT reload failure");
            return;
        }
        try {
            runtimeRoot.authorizeRemoteSources(sources, root);
        } catch (Exception failure) {
            NekoJS.LOGGER.error("Failed to restore the previous server pack trust set after CLIENT reload failure", failure);
        }
    }

    private static String syncIdOf(ScriptPack pack) {
        String encoded = pack.root().getFileName().toString();
        int separator = encoded.indexOf('_');
        return separator > 0
                ? encoded.substring(0, separator) + ":" + encoded.substring(separator + 1)
                : encoded;
    }

    private static List<NekoTrustContext.RemoteSource> remoteSources(List<ScriptPack> packs) {
        List<NekoTrustContext.RemoteSource> sources = new ArrayList<>();
        for (ScriptPack pack : packs) {
            JsonObject signature = pack.manifest() == null ? null : pack.manifest().signature();
            String keyId = signature == null ? null : PackSignatureVerifier.string(signature, "keyId");
            if (keyId == null || keyId.isBlank()) {
                return null;
            }
            try (var files = Files.walk(pack.root())) {
                files.filter(Files::isRegularFile)
                        .filter(file -> !file.getFileName().toString().equals("manifest.json"))
                        .forEach(file -> sources.add(new NekoTrustContext.RemoteSource(
                                file, syncIdOf(pack), keyId)));
            } catch (IOException failure) {
                return null;
            }
        }
        return List.copyOf(sources);
    }

    private static boolean reloadClientScripts(String reason) {
        BooleanSupplier hook = clientReloadHook;
        if (hook == null) {
            NekoJS.LOGGER.debug("No client reload hook installed, skipping CLIENT reload ({})", reason);
            return true;
        }
        try {
            return hook.getAsBoolean();
        } catch (Throwable e) {
            NekoJS.LOGGER.error("CLIENT script reload after pack sync failed", e);
            return false;
        }
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
        if (!verified.valid() || verified.fingerprint() == null) return;
        trustStore.trustPublicKey(keyId, publicKey, verified.fingerprint(), activeAddress == null ? "unknown" : activeAddress);
    }

    private static List<NekoTrustContext.RemoteSource> remoteSources(
            Path bucketDir, Map<String, ServerPackCache.CachedPack> resolved) {
        List<NekoTrustContext.RemoteSource> sources = new ArrayList<>();
        for (Map.Entry<String, ServerPackCache.CachedPack> entry : resolved.entrySet()) {
            JsonObject signature = PackSignatureVerifier.parseSignatureBlock(entry.getValue().manifestJson());
            String keyId = signature == null ? null : PackSignatureVerifier.string(signature, "keyId");
            if ((keyId == null || keyId.isBlank()) && !entry.getValue().files().isEmpty()) return null;
            if (keyId == null || keyId.isBlank()) continue;
            Path packDir = bucketDir.resolve(SyncedPack.encodeSyncId(entry.getKey()));
            for (PackContentFile file : entry.getValue().files()) {
                Path materialized = ServerPackCache.resolveInside(packDir, file.relativePath());
                if (materialized != null) {
                    sources.add(new NekoTrustContext.RemoteSource(materialized, entry.getKey(), keyId));
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

    private static boolean clientModeOff() {
        return !ClassFilter.INSTANCE.config().packSyncEnabled();
    }

    private static boolean clientModeHashOnly() {
        return SandboxConfig.PACK_SYNC_HASH_ONLY.equalsIgnoreCase(ClassFilter.INSTANCE.config().packSyncMode());
    }

    private static boolean allowUnsigned() {
        return ClassFilter.INSTANCE.config().packSyncAllowUnsigned();
    }

    /** 哈希清单条目（平台 payload → common 的映射单位）。 */
    public record HashEntry(String syncId, String hash) {}

    private record ActiveState(String address, String bucket, Map<String, String> expectedHashes,
                               List<ScriptPack> activePacks, Path activeRoot, Path remoteRoot) {
        private ActiveState {
            expectedHashes = Map.copyOf(expectedHashes == null ? Map.of() : expectedHashes);
            activePacks = List.copyOf(activePacks == null ? List.of() : activePacks);
        }
    }

    /** bundle 处理结果：disconnect 非空时平台断连并展示消息。 */
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
