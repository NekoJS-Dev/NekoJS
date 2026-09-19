package com.tkisor.nekojs.core.pack.sync;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.NekoCoreContext;
import com.tkisor.nekojs.core.NekoSandboxFactory;
import com.tkisor.nekojs.core.NekoSharedEngine;
import com.tkisor.nekojs.core.ScriptEventBridge;
import com.tkisor.nekojs.core.error.DefaultErrorTracker;
import com.tkisor.nekojs.core.error.SourceMapRegistry;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.lifecycle.NekoRuntimeRoot;
import com.tkisor.nekojs.core.module.NekoModuleError;
import com.tkisor.nekojs.core.module.NekoModulePipeline;
import com.tkisor.nekojs.core.module.NekoModulePipelineCache;
import com.tkisor.nekojs.core.module.NekoTrustApprovedSource;
import com.tkisor.nekojs.core.module.NekoRuntimeTrustContext;
import com.tkisor.nekojs.core.module.NekoEsmVirtualModuleRegistry;
import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.pack.ScriptPack;
import com.tkisor.nekojs.core.pack.ScriptPackRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 客户端同步管线集成测试：签名 → 落盘 → 重扫哈希对照 → 信任 → 激活/重载 的全链路，
 * 以及各拒绝分支（未签名、未信任、哈希错配）的断连语义与 hashOnly 客户端模式。
 */
class PackSyncClientTest {

    @BeforeAll
    static void initPlatform() throws Exception {
        TestPlatformInit.ensureInitialized();
        // 隔离：清掉上一轮测试 JVM 留在共享 gamedir 的信任库（keyId pinning 会跨轮残留，
        // 与本轮随机生成的签名密钥冲突——等价玩家换钥场景，但非本测试关注点）
        java.nio.file.Files.deleteIfExists(
            com.tkisor.nekojs.core.fs.NekoJSPaths.get().config().resolve(PackSyncTrustStore.FILE_NAME));
    }

    private final AtomicInteger reloads = new AtomicInteger();
    private NekoRuntimeTrustContext runtimeTrust;
    private NekoRuntimeRoot runtimeRoot;

    @BeforeEach
    void installTestRuntimeBinding() {
        runtimeTrust = NekoRuntimeTrustContext.local();
        runtimeRoot = runtimeRoot(runtimeTrust);
    }

    @AfterEach
    void cleanup() {
        ClassFilter.INSTANCE.updateConfig(SandboxConfig.defaultConfig());
        PackSyncClient.installClientReloadHook(null);
        PackSyncClient.handleDisconnect(runtimeRoot);
        runtimeRoot.closeSilently();
    }

    @Test
    void unsignedPackRejectedByDefault() {
        config("all", false);
        String manifest = "{\"id\": \"demo\", \"version\": \"1.0.0\"}";
        SyncedPack pack = pack("packs:demo", "GLOBAL", manifest, "client_scripts/hud.js", "hud()");
        PackSyncClient.handleHashList(runtimeRoot, "srv-unsigned.test", hashes(pack));

        PackSyncClient.Outcome outcome = PackSyncClient.handleBundle(runtimeRoot, List.of(pack));

        assertTrue(outcome.shouldDisconnect());
        assertTrue(outcome.disconnect().contains("unsigned"));
        assertTrue(ScriptPackRegistry.get().serverCachePacks().isEmpty());
        assertEquals(0, reloads.get());
    }

    @Test
    void untrustedServerDisconnectsWithTrustHint() {
        config("all", false);
        String manifest = signed("packs:demo", "GLOBAL", "key-untrusted");
        SyncedPack pack = pack("packs:demo", "GLOBAL", manifest, "client_scripts/hud.js", "hud()");
        PackSyncClient.handleHashList(runtimeRoot, "srv-untrusted.test", hashes(pack));

        PackSyncClient.Outcome outcome = PackSyncClient.handleBundle(runtimeRoot, List.of(pack));

        assertTrue(outcome.shouldDisconnect());
        assertTrue(outcome.disconnect().contains("/nekojs trust srv-untrusted.test"));
        assertTrue(ScriptPackRegistry.get().serverCachePacks().isEmpty());
    }

    @Test
    void trustedServerActivatesAndReloads() {
        config("all", false);
        installCountingReloadHook();
        String manifest = signed("packs:demo", "GLOBAL", "key-trusted");
        SyncedPack pack = pack("packs:demo", "GLOBAL", manifest, "client_scripts/hud.js", "hud()");
        PackSyncClient.handleHashList(runtimeRoot, "srv-trusted.test", hashes(pack));
        PackSyncTrustStore.get().trustServer("srv-trusted.test");

        PackSyncClient.Outcome outcome = PackSyncClient.handleBundle(runtimeRoot, List.of(pack));

        assertNull(outcome.disconnect());
        List<ScriptPack> active = ScriptPackRegistry.get().serverCachePacks();
        assertEquals(1, active.size());
        assertEquals("demo", active.get(0).id());
        assertTrue(active.get(0).enabled());
        assertEquals(1, reloads.get()); // 激活后触发一次 CLIENT 重载

        // 信任服务器即信任其签名密钥（v1 pinning）：keyId 已入信任库
        JsonObject signature = JsonParser.parseString(manifest).getAsJsonObject().getAsJsonObject("signature");
        String keyId = signature.get("keyId").getAsString();
        assertTrue(PackSyncTrustStore.get().trustedPublicKey(keyId) != null);

        // 断线：卸载并再次重载
        PackSyncClient.handleDisconnect(runtimeRoot);
        assertTrue(ScriptPackRegistry.get().serverCachePacks().isEmpty());
        assertEquals(2, reloads.get());
    }

    @Test
    void reloadFailureRejectsBundleAndRevokesSelectedRuntimeState() {
        config("all", false);
        PackSyncClient.installClientReloadHook(() -> false);
        String syncId = "packs:reload-failure";
        String manifest = signed(syncId, "GLOBAL", "key-reload-failure-" + System.nanoTime(),
                "client_scripts/hud.js", "module.exports = 'failure';\n");
        SyncedPack pack = pack(syncId, "GLOBAL", manifest,
                "client_scripts/hud.js", "module.exports = 'failure';\n");
        PackSyncClient.handleHashList(runtimeRoot, "srv-reload-failure.test", hashes(pack));
        PackSyncTrustStore.get().trustServer("srv-reload-failure.test");

        PackSyncClient.Outcome outcome = PackSyncClient.handleBundle(runtimeRoot, List.of(pack));

        assertTrue(outcome.shouldDisconnect(), "a failed client reload must reject the bundle");
        assertTrue(outcome.disconnect().contains("reload"));
        assertTrue(ScriptPackRegistry.get().serverCachePacks().isEmpty(),
                "the selected server cache must not remain active after reload failure");
        Path remoteFile = ServerPackCache.bucketDir(PackSyncTrustStore.bucketFor("srv-reload-failure.test"))
                .resolve(SyncedPack.encodeSyncId(pack.syncId())).resolve("client_scripts/hud.js");
        assertNull(runtimeTrust.approvalFor(remoteFile),
                "remote credentials must be revoked with the rejected selection");
    }

    @Test
    void failedHashListDeactivationRestoresPreviousStateAndRejectsConnection() {
        config("all", false);
        String oldId = "packs:hash-list-old";
        String oldManifest = signed(oldId, "GLOBAL", "key-hash-list-old",
                "client_scripts/old.js", "module.exports = 'old';\n");
        SyncedPack oldPack = pack(oldId, "GLOBAL", oldManifest,
                "client_scripts/old.js", "module.exports = 'old';\n");
        PackSyncClient.installClientReloadHook(() -> true);
        PackSyncClient.handleHashList(runtimeRoot, "srv-hash-list.test", hashes(oldPack));
        PackSyncTrustStore.get().trustServer("srv-hash-list.test");
        assertFalse(PackSyncClient.handleBundle(runtimeRoot, List.of(oldPack)).shouldDisconnect());

        String newId = "packs:hash-list-new";
        SyncedPack newPack = pack(newId, "GLOBAL",
                signed(newId, "GLOBAL", "key-hash-list-new", "client_scripts/new.js", "new();\n"),
                "client_scripts/new.js", "new();\n");
        PackSyncClient.installClientReloadHook(() -> false);

        PackSyncClient.Outcome outcome = PackSyncClient.handleHashList(
                runtimeRoot, "srv-hash-list.test", hashes(newPack));

        assertTrue(outcome.shouldDisconnect(), "a rejected deactivation must disconnect the client");
        assertTrue(outcome.disconnect().contains("deactivation"));
        assertEquals(1, ScriptPackRegistry.get().serverCachePacks().size(),
                "the previous active registry must be restored");
        Path oldFile = ServerPackCache.bucketDir(PackSyncTrustStore.bucketFor("srv-hash-list.test"))
                .resolve(SyncedPack.encodeSyncId(oldId)).resolve("client_scripts/old.js");
        assertNotNull(runtimeTrust.approvalFor(oldFile),
                "the previous remote credential must be restored");

        // The old hash list/address state was restored as well: the old bundle is still accepted
        // after the connection-level failure is cleared.
        PackSyncClient.installClientReloadHook(() -> true);
        assertFalse(PackSyncClient.handleBundle(runtimeRoot, List.of(oldPack)).shouldDisconnect());
    }

    @Test
    void failedReplacementRestoresThePreviousActiveRegistryAndCredentials() {
        config("all", false);
        String syncId = "packs:reload-rollback";
        String manifest = signed(syncId, "GLOBAL", "key-reload-rollback-" + System.nanoTime(),
                "client_scripts/hud.js", "module.exports = 'rollback';\n");
        SyncedPack pack = pack(syncId, "GLOBAL", manifest,
                "client_scripts/hud.js", "module.exports = 'rollback';\n");
        PackSignatureVerifier.Result signatureResult = PackSignatureVerifier.verify(
                pack.syncId(), pack.scopeName(), pack.manifestJson(), pack.files(), true, PackSyncTrustStore.get());
        assertTrue(signatureResult.valid(), signatureResult.reason());
        PackSyncClient.installClientReloadHook(() -> true);
        PackSyncClient.handleHashList(runtimeRoot, "srv-reload-rollback.test", hashes(pack));
        PackSyncTrustStore.get().trustServer("srv-reload-rollback.test");
        PackSyncClient.Outcome initial = PackSyncClient.handleBundle(runtimeRoot, List.of(pack));
        assertFalse(initial.shouldDisconnect(), initial.disconnect());

        PackSyncClient.installClientReloadHook(() -> false);
        PackSyncClient.handleHashList(runtimeRoot, "srv-reload-rollback.test", hashes(pack));
        PackSyncClient.Outcome outcome = PackSyncClient.handleBundle(runtimeRoot, List.of(pack));

        assertTrue(outcome.shouldDisconnect());
        assertEquals(1, ScriptPackRegistry.get().serverCachePacks().size(),
                "a failed replacement must leave the old active registry when the old runtime remains live");
        Path remoteFile = ServerPackCache.bucketDir(PackSyncTrustStore.bucketFor("srv-reload-rollback.test"))
                .resolve(SyncedPack.encodeSyncId(pack.syncId())).resolve("client_scripts/hud.js");
        assertNotNull(runtimeTrust.approvalFor(remoteFile),
                 "the old active runtime credential must be restored with the registry");
    }

    @Test
    void failedSameBucketReplacementRestoresOldPhysicalFilesAndRuntime() throws Exception {
        config("all", false);
        String address = "srv-same-bucket-rollback.test";
        String syncId = "packs:same-bucket-rollback";
        KeyPair keyPair = PackSigner.generateKeyPair();
        SyncedPack oldPack = pack(syncId, "GLOBAL",
                signed(keyPair, syncId, "GLOBAL", "key-same-bucket",
                        "client_scripts/hud.js", "module.exports = 'old';\n"),
                "client_scripts/hud.js", "module.exports = 'old';\n");
        SyncedPack replacement = pack(syncId, "GLOBAL",
                signed(keyPair, syncId, "GLOBAL", "key-same-bucket",
                        "client_scripts/hud.js", "module.exports = 'replacement';\n"),
                "client_scripts/hud.js", "module.exports = 'replacement';\n");

        PackSyncClient.installClientReloadHook(() -> true);
        PackSyncClient.handleHashList(runtimeRoot, address, hashes(oldPack));
        PackSyncTrustStore.get().trustServer(address);
        assertFalse(PackSyncClient.handleBundle(runtimeRoot, List.of(oldPack)).shouldDisconnect());

        PackSyncClient.installClientReloadHook(() -> false);
        PackSyncClient.handleHashList(runtimeRoot, address, hashes(replacement));
        assertEquals(1, ScriptPackRegistry.get().serverCachePacks().size(),
                "same-bucket replacement must keep the old active set pending bundle validation");
        PackSyncClient.Outcome outcome = PackSyncClient.handleBundle(runtimeRoot, List.of(replacement));

        assertTrue(outcome.shouldDisconnect());
        Path bucket = ServerPackCache.bucketDir(PackSyncTrustStore.bucketFor(address));
        Path oldFile = bucket.resolve(SyncedPack.encodeSyncId(syncId)).resolve("client_scripts/hud.js");
        assertEquals("module.exports = 'old';\n", java.nio.file.Files.readString(oldFile),
                "reload failure must restore the previous same-bucket physical file");
        assertEquals(1, ScriptPackRegistry.get().serverCachePacks().size());
        assertEquals(oldFile.getParent().getParent(), ScriptPackRegistry.get().serverCachePacks().get(0).root(),
                "old active registry must be restored");
        assertNotNull(runtimeTrust.approvalFor(oldFile),
                "the previous remote approval must be restored for the old physical file");

        // The restored hash-list state must still describe the old active bundle.
        PackSyncClient.installClientReloadHook(() -> true);
        PackSyncClient.Outcome restored = PackSyncClient.handleBundle(runtimeRoot, List.of(oldPack));
        assertFalse(restored.shouldDisconnect(), restored.disconnect());
    }

    @Test
    void successfulActivationAuthorizesRuntimeCacheAndDisconnectRevokesIt() throws Exception {
        config("all", false);
        String manifest = signed("packs:runtime", "GLOBAL", "key-runtime");
        SyncedPack pack = pack("packs:runtime", "GLOBAL", manifest,
                "client_scripts/hud.js", "hud()");
        PackSyncClient.handleHashList(runtimeRoot, "srv-runtime.test", hashes(pack));
        PackSyncTrustStore.get().trustServer("srv-runtime.test");

        assertNull(PackSyncClient.handleBundle(runtimeRoot, List.of(pack)).disconnect());

        Path remoteFile = ServerPackCache.bucketDir(PackSyncTrustStore.bucketFor("srv-runtime.test"))
                .resolve(SyncedPack.encodeSyncId("packs:runtime"))
                .resolve("client_scripts/hud.js");
        NekoModulePipelineCache cache = new NekoModulePipelineCache(
                new NekoModulePipeline(new NekoCompilationPipeline(),
                        ScriptCompilerRegistry.createRuntimeRegistry(), SandboxConfig.defaultConfig()),
                new SourceMapRegistry(NekoJSPaths.get().root()),
                new NekoEsmVirtualModuleRegistry(NekoJSPaths.get().root()),
                runtimeTrust);
        try {
            assertEquals(NekoTrustApprovedSource.Kind.REMOTE_AUTHORIZED,
                    runtimeTrust.approvalFor(remoteFile).kind());
            assertNotNull(cache.prepare(remoteFile), "the currently authorized source must remain executable");

            PackSyncClient.handleDisconnect(runtimeRoot);

            Exception denied = org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                    () -> cache.prepare(remoteFile));
            NekoModuleError staged = org.junit.jupiter.api.Assertions.assertInstanceOf(NekoModuleError.class, denied);
            assertEquals(NekoModuleError.Stage.PREPARE, staged.stage());
            assertEquals(NekoModuleError.OWNER_PACK_TRUST, staged.owner());
            assertTrue(staged.sourcePath().replace('\\', '/').endsWith("hud.js"), staged.detail());
        } finally {
            cache.clear();
        }
    }

    @Test
    void replacingBundleRejectsOldAndStaleFilesButAllowsCurrentSource() throws Exception {
        config("all", false);
        String oldManifest = signed("packs:stale-old", "GLOBAL", "key-stale-old",
                "client_scripts/old.js", "module.exports = 'old';\n");
        SyncedPack oldPack = pack("packs:stale-old", "GLOBAL", oldManifest,
                "client_scripts/old.js", "module.exports = 'old';\n");
        PackSyncClient.handleHashList(runtimeRoot, "srv-stale.test", hashes(oldPack));
        PackSyncTrustStore.get().trustServer("srv-stale.test");
        assertNull(PackSyncClient.handleBundle(runtimeRoot, List.of(oldPack)).disconnect());

        String newManifest = signed("packs:stale-new", "GLOBAL", "key-stale-new",
                "client_scripts/new.js", "module.exports = 'new';\n");
        SyncedPack newPack = pack("packs:stale-new", "GLOBAL", newManifest,
                "client_scripts/new.js", "module.exports = 'new';\n");
        PackSyncClient.handleHashList(runtimeRoot, "srv-stale.test", hashes(newPack));
        assertNull(PackSyncClient.handleBundle(runtimeRoot, List.of(newPack)).disconnect());

        Path bucket = ServerPackCache.bucketDir(PackSyncTrustStore.bucketFor("srv-stale.test"));
        Path oldDir = bucket.resolve(SyncedPack.encodeSyncId(oldPack.syncId()));
        Path newDir = bucket.resolve(SyncedPack.encodeSyncId(newPack.syncId()));
        Path oldFile = oldDir.resolve("client_scripts/old.js");
        Path newFile = newDir.resolve("client_scripts/new.js");
        assertTrue(java.nio.file.Files.isRegularFile(oldFile), "the old cache file is intentionally retained");
        List<ScriptPack> active = ScriptPackRegistry.get().serverCachePacks();
        assertEquals(1, active.size(), "the active registry must contain only the current syncId set");
        assertEquals(newDir, active.get(0).root(),
                "the current syncId directory must be active");
        assertFalse(active.stream().anyMatch(pack -> pack.root().equals(oldDir)),
                "retained stale cache directories must not become active packs");

        NekoModulePipelineCache cache = new NekoModulePipelineCache(
                new NekoModulePipeline(new NekoCompilationPipeline(),
                        ScriptCompilerRegistry.createRuntimeRegistry(), SandboxConfig.defaultConfig()),
                new SourceMapRegistry(NekoJSPaths.get().root()),
                new NekoEsmVirtualModuleRegistry(NekoJSPaths.get().root()),
                runtimeTrust);
        try {
            assertNotNull(cache.prepare(newFile), "the current bundle source must be executable");
            Exception denied = org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                    () -> cache.prepare(oldFile));
            NekoModuleError staged = org.junit.jupiter.api.Assertions.assertInstanceOf(NekoModuleError.class, denied);
            assertEquals(NekoModuleError.Stage.PREPARE, staged.stage());
            assertEquals(NekoModuleError.OWNER_PACK_TRUST, staged.owner());
        } finally {
            cache.clear();
        }
    }

    @Test
    void switchingServerBucketsRevokesPreviousRuntimeSourcesBeforeActivatingNext() throws Exception {
        config("all", false);
        String firstAddress = "srv-switch-alpha.test";
        String secondAddress = "srv-switch-beta.test";
        assertNotEquals(PackSyncTrustStore.bucketFor(firstAddress), PackSyncTrustStore.bucketFor(secondAddress));

        String firstManifest = signed("packs:switch-first", "GLOBAL", "key-switch-first",
                "client_scripts/first.js", "module.exports = 'first';\n");
        SyncedPack first = pack("packs:switch-first", "GLOBAL", firstManifest,
                "client_scripts/first.js", "module.exports = 'first';\n");
        PackSyncClient.handleHashList(runtimeRoot, firstAddress, hashes(first));
        PackSyncTrustStore.get().trustServer(firstAddress);
        assertNull(PackSyncClient.handleBundle(runtimeRoot, List.of(first)).disconnect());

        String secondManifest = signed("packs:switch-second", "GLOBAL", "key-switch-second",
                "client_scripts/second.js", "module.exports = 'second';\n");
        SyncedPack second = pack("packs:switch-second", "GLOBAL", secondManifest,
                "client_scripts/second.js", "module.exports = 'second';\n");
        PackSyncClient.handleHashList(runtimeRoot, secondAddress, hashes(second));
        assertTrue(ScriptPackRegistry.get().serverCachePacks().isEmpty(),
                "changing server buckets must deactivate the previous active set first");
        PackSyncTrustStore.get().trustServer(secondAddress);
        assertNull(PackSyncClient.handleBundle(runtimeRoot, List.of(second)).disconnect());

        Path firstFile = ServerPackCache.bucketDir(PackSyncTrustStore.bucketFor(firstAddress))
                .resolve(SyncedPack.encodeSyncId(first.syncId())).resolve("client_scripts/first.js");
        Path secondFile = ServerPackCache.bucketDir(PackSyncTrustStore.bucketFor(secondAddress))
                .resolve(SyncedPack.encodeSyncId(second.syncId())).resolve("client_scripts/second.js");
        NekoModulePipelineCache cache = new NekoModulePipelineCache(
                new NekoModulePipeline(new NekoCompilationPipeline(),
                        ScriptCompilerRegistry.createRuntimeRegistry(), SandboxConfig.defaultConfig()),
                new SourceMapRegistry(NekoJSPaths.get().root()),
                new NekoEsmVirtualModuleRegistry(NekoJSPaths.get().root()),
                runtimeTrust);
        try {
            assertNotNull(cache.prepare(secondFile), "the current server bucket remains authorized");
            Exception denied = org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                    () -> cache.prepare(firstFile));
            NekoModuleError staged = org.junit.jupiter.api.Assertions.assertInstanceOf(NekoModuleError.class, denied);
            assertEquals(NekoModuleError.OWNER_PACK_TRUST, staged.owner());
        } finally {
            cache.clear();
        }
    }

    @Test
    void hashMismatchAfterPersistDisconnects() {
        config("all", false);
        String manifest = signed("packs:demo", "GLOBAL", "key-mismatch");
        SyncedPack pack = pack("packs:demo", "GLOBAL", manifest, "client_scripts/hud.js", "hud()");
        // 哈希清单被篡改：预期哈希与 bundle 实际内容不一致 → 落盘重扫后检出
        List<PackSyncClient.HashEntry> wrong = List.of(new PackSyncClient.HashEntry("packs:demo", "deadbeef"));
        PackSyncClient.handleHashList(runtimeRoot, "srv-mismatch.test", wrong);

        PackSyncClient.Outcome outcome = PackSyncClient.handleBundle(runtimeRoot, List.of(pack));

        assertTrue(outcome.shouldDisconnect());
        assertTrue(outcome.disconnect().contains("integrity check failed"));
    }

    @Test
    void hashOnlyClientNeverExecutesAndEmptyListClears() {
        // 先以 all 模式激活一个包（复用信任 + bundle 流程）
        config("all", false);
        installCountingReloadHook();
        String manifest = signed("packs:demo", "GLOBAL", "key-hashonly");
        SyncedPack pack = pack("packs:demo", "GLOBAL", manifest, "client_scripts/hud.js", "hud()");
        PackSyncClient.handleHashList(runtimeRoot, "srv-hashonly.test", hashes(pack));
        PackSyncTrustStore.get().trustServer("srv-hashonly.test");
        assertNull(PackSyncClient.handleBundle(runtimeRoot, List.of(pack)).disconnect());
        assertEquals(1, ScriptPackRegistry.get().serverCachePacks().size());

        // 切到 hashOnly：bundle 被忽略
        config("hashOnly", false);
        PackSyncClient.handleHashList(runtimeRoot, "srv-hashonly.test", hashes(pack));
        assertTrue(ScriptPackRegistry.get().serverCachePacks().isEmpty());
        assertEquals(2, reloads.get()); // hashOnly 清空时触发重载
        assertNull(PackSyncClient.handleBundle(runtimeRoot, List.of(pack)).disconnect());
        assertTrue(ScriptPackRegistry.get().serverCachePacks().isEmpty());

        // 空清单同样清空（幂等：已空则不再重载）
        PackSyncClient.handleHashList(runtimeRoot, "srv-hashonly.test", List.of());
        assertEquals(2, reloads.get());
    }

    @Test
    void clientModeOffIgnoresEverything() {
        config("off", false);
        installCountingReloadHook();
        String manifest = "{\"id\": \"demo\", \"version\": \"1.0.0\"}";
        SyncedPack pack = pack("packs:demo", "GLOBAL", manifest, "client_scripts/hud.js", "hud()");

        PackSyncClient.handleHashList(runtimeRoot, "srv-off.test", hashes(pack));
        assertNull(PackSyncClient.handleBundle(runtimeRoot, List.of(pack)).disconnect());

        assertTrue(ScriptPackRegistry.get().serverCachePacks().isEmpty());
        assertEquals(0, reloads.get());
    }

    /* ================= 辅助 ================= */

    private static NekoRuntimeRoot runtimeRoot(NekoRuntimeTrustContext trustContext) {
        NekoJSPaths paths = NekoJSPaths.get();
        SandboxConfig config = SandboxConfig.defaultConfig();
        ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        NekoModulePipelineCache cache = new NekoModulePipelineCache(
                new NekoModulePipeline(new NekoCompilationPipeline(), compilers, config),
                new SourceMapRegistry(paths.root()), new NekoEsmVirtualModuleRegistry(paths.root()), trustContext);
        IPluginRuntime plugins = (IPluginRuntime) Proxy.newProxyInstance(
                PackSyncClientTest.class.getClassLoader(), new Class<?>[]{IPluginRuntime.class},
                new EmptyPluginRuntime());
        NekoCoreContext core = new NekoCoreContext(
                NekoSharedEngine.get(), config, ClassFilter.INSTANCE, new DefaultErrorTracker(paths, config));
        NekoSandboxFactory sandboxFactory = new NekoSandboxFactory(
                core, paths, compilers, plugins, cache);
        return new NekoRuntimeRoot(core, plugins, ScriptEventBridge.EMPTY,
                new ScriptPropertyRegistry.Impl(), sandboxFactory, cache, trustContext);
    }

    private static final class EmptyPluginRuntime implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            Class<?> returnType = method.getReturnType();
            if (returnType == boolean.class) return false;
            if (returnType == int.class) return 0;
            if (returnType == long.class) return 0L;
            if (returnType == void.class) return null;
            if (returnType == Map.class) return Map.of();
            if (returnType == List.class) return List.of();
            if (returnType == java.util.Set.class) return java.util.Set.of();
            if (returnType == java.util.Collection.class) return List.of();
            if (returnType == java.util.Optional.class) return java.util.Optional.empty();
            return null;
        }
    }

    private void config(String packSyncMode, boolean allowUnsigned) {
        ClassFilter.INSTANCE.updateConfig(new SandboxConfig(
            false, false, false, false, true, true, false, true, 30, 0, 0,
            packSyncMode, allowUnsigned, false));
    }

    private void installCountingReloadHook() {
        reloads.set(0);
        PackSyncClient.installClientReloadHook(() -> {
            reloads.incrementAndGet();
            return true;
        });
    }

    private static List<PackSyncClient.HashEntry> hashes(SyncedPack pack) {
        return List.of(new PackSyncClient.HashEntry(pack.syncId(), pack.hash()));
    }

    private static SyncedPack pack(String syncId, String scope, String manifestJson, String path, String content) {
        List<PackContentFile> files = List.of(new PackContentFile(path, content.getBytes()));
        return SyncedPack.of(
            syncId, scope,
            PackHasher.hash(manifestJson.getBytes(java.nio.charset.StandardCharsets.UTF_8), files),
            manifestJson, files);
    }

    private static String signed(String syncId, String scope, String keyId) {
        return signed(syncId, scope, keyId, "client_scripts/hud.js", "hud()");
    }

    private static String signed(String syncId, String scope, String keyId, String path, String content) {
        KeyPair keyPair = PackSigner.generateKeyPair();
        return signed(keyPair, syncId, scope, keyId, path, content);
    }

    private static String signed(KeyPair keyPair, String syncId, String scope, String keyId,
                                 String path, String content) {
        String unsigned = "{\"id\": \"demo\", \"version\": \"1.0.0\"}";
        List<PackContentFile> files = List.of(new PackContentFile(path, content.getBytes()));
        JsonObject signature = PackSigner.sign(keyId, keyPair, syncId, scope, unsigned, files);
        JsonObject root = JsonParser.parseString(unsigned).getAsJsonObject();
        root.add("signature", signature);
        return root.toString();
    }
}
