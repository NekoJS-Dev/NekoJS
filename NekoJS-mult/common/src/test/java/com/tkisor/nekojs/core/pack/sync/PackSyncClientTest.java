package com.tkisor.nekojs.core.pack.sync;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.module.NekoModuleError;
import com.tkisor.nekojs.core.module.NekoModulePipeline;
import com.tkisor.nekojs.core.module.NekoModulePipelineCache;
import com.tkisor.nekojs.core.module.NekoTrustApprovedSource;
import com.tkisor.nekojs.core.module.NekoTrustContext;
import com.tkisor.nekojs.core.module.NekoRuntimeTrustContext;
import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.pack.ScriptPack;
import com.tkisor.nekojs.core.pack.ScriptPackRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    @BeforeEach
    void installTestRuntimeBinding() {
        PackSyncClient.installClientRemoteTrustHook(new PackSyncClient.RemoteTrustHook() {
            @Override
            public void authorize(List<NekoTrustContext.RemoteSource> sources, Path remoteRoot) {
                // Most tests exercise pack acceptance/rejection only; the integration test below
                // installs a real runtime-owned context.
            }

            @Override
            public void revoke(Path remoteRoot) {
            }
        });
    }

    @AfterEach
    void cleanup() {
        ClassFilter.INSTANCE.updateConfig(SandboxConfig.defaultConfig());
        PackSyncClient.installClientReloadHook(null);
        PackSyncClient.installClientRemoteTrustHook(null);
        PackSyncClient.handleDisconnect();
    }

    @Test
    void unsignedPackRejectedByDefault() {
        config("all", false);
        String manifest = "{\"id\": \"demo\", \"version\": \"1.0.0\"}";
        SyncedPack pack = pack("packs:demo", "GLOBAL", manifest, "client_scripts/hud.js", "hud()");
        PackSyncClient.handleHashList("srv-unsigned.test", hashes(pack));

        PackSyncClient.Outcome outcome = PackSyncClient.handleBundle(List.of(pack));

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
        PackSyncClient.handleHashList("srv-untrusted.test", hashes(pack));

        PackSyncClient.Outcome outcome = PackSyncClient.handleBundle(List.of(pack));

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
        PackSyncClient.handleHashList("srv-trusted.test", hashes(pack));
        PackSyncTrustStore.get().trustServer("srv-trusted.test");

        PackSyncClient.Outcome outcome = PackSyncClient.handleBundle(List.of(pack));

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
        PackSyncClient.handleDisconnect();
        assertTrue(ScriptPackRegistry.get().serverCachePacks().isEmpty());
        assertEquals(2, reloads.get());
    }

    @Test
    void successfulActivationAuthorizesRuntimeCacheAndDisconnectRevokesIt() throws Exception {
        config("all", false);
        NekoRuntimeTrustContext runtimeTrust = NekoRuntimeTrustContext.local();
        PackSyncClient.installClientRemoteTrustHook(new PackSyncClient.RemoteTrustHook() {
            @Override
            public void authorize(List<NekoTrustContext.RemoteSource> sources, Path remoteRoot) {
                runtimeTrust.authorizeRemoteSources(sources, remoteRoot);
            }

            @Override
            public void revoke(Path remoteRoot) {
                runtimeTrust.revokeRemoteSources(remoteRoot);
            }
        });

        String manifest = signed("packs:runtime", "GLOBAL", "key-runtime");
        SyncedPack pack = pack("packs:runtime", "GLOBAL", manifest,
                "client_scripts/hud.js", "hud()");
        PackSyncClient.handleHashList("srv-runtime.test", hashes(pack));
        PackSyncTrustStore.get().trustServer("srv-runtime.test");

        assertNull(PackSyncClient.handleBundle(List.of(pack)).disconnect());

        Path remoteFile = ServerPackCache.bucketDir(PackSyncTrustStore.bucketFor("srv-runtime.test"))
                .resolve(SyncedPack.encodeSyncId("packs:runtime"))
                .resolve("client_scripts/hud.js");
        NekoModulePipelineCache cache = new NekoModulePipelineCache(
                new NekoModulePipeline(new NekoCompilationPipeline(),
                        ScriptCompilerRegistry.createRuntimeRegistry(), SandboxConfig.defaultConfig()),
                runtimeTrust);
        try {
            assertEquals(NekoTrustApprovedSource.Kind.REMOTE_AUTHORIZED,
                    cache.approvedSource(remoteFile).kind());
            assertNotNull(cache.prepare(remoteFile), "the currently authorized source must remain executable");

            PackSyncClient.handleDisconnect();

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
        NekoRuntimeTrustContext runtimeTrust = NekoRuntimeTrustContext.local();
        PackSyncClient.installClientRemoteTrustHook(new PackSyncClient.RemoteTrustHook() {
            @Override
            public void authorize(List<NekoTrustContext.RemoteSource> sources, Path remoteRoot) {
                runtimeTrust.authorizeRemoteSources(sources, remoteRoot);
            }

            @Override
            public void revoke(Path remoteRoot) {
                runtimeTrust.revokeRemoteSources(remoteRoot);
            }
        });

        String oldManifest = signed("packs:stale-old", "GLOBAL", "key-stale-old",
                "client_scripts/old.js", "module.exports = 'old';\n");
        SyncedPack oldPack = pack("packs:stale-old", "GLOBAL", oldManifest,
                "client_scripts/old.js", "module.exports = 'old';\n");
        PackSyncClient.handleHashList("srv-stale.test", hashes(oldPack));
        PackSyncTrustStore.get().trustServer("srv-stale.test");
        assertNull(PackSyncClient.handleBundle(List.of(oldPack)).disconnect());

        String newManifest = signed("packs:stale-new", "GLOBAL", "key-stale-new",
                "client_scripts/new.js", "module.exports = 'new';\n");
        SyncedPack newPack = pack("packs:stale-new", "GLOBAL", newManifest,
                "client_scripts/new.js", "module.exports = 'new';\n");
        PackSyncClient.handleHashList("srv-stale.test", hashes(newPack));
        assertNull(PackSyncClient.handleBundle(List.of(newPack)).disconnect());

        Path bucket = ServerPackCache.bucketDir(PackSyncTrustStore.bucketFor("srv-stale.test"));
        Path oldFile = bucket.resolve(SyncedPack.encodeSyncId(oldPack.syncId())).resolve("client_scripts/old.js");
        Path newFile = bucket.resolve(SyncedPack.encodeSyncId(newPack.syncId())).resolve("client_scripts/new.js");
        assertTrue(java.nio.file.Files.isRegularFile(oldFile), "the old cache file is intentionally retained");

        NekoModulePipelineCache cache = new NekoModulePipelineCache(
                new NekoModulePipeline(new NekoCompilationPipeline(),
                        ScriptCompilerRegistry.createRuntimeRegistry(), SandboxConfig.defaultConfig()),
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
    void hashMismatchAfterPersistDisconnects() {
        config("all", false);
        String manifest = signed("packs:demo", "GLOBAL", "key-mismatch");
        SyncedPack pack = pack("packs:demo", "GLOBAL", manifest, "client_scripts/hud.js", "hud()");
        // 哈希清单被篡改：预期哈希与 bundle 实际内容不一致 → 落盘重扫后检出
        List<PackSyncClient.HashEntry> wrong = List.of(new PackSyncClient.HashEntry("packs:demo", "deadbeef"));
        PackSyncClient.handleHashList("srv-mismatch.test", wrong);

        PackSyncClient.Outcome outcome = PackSyncClient.handleBundle(List.of(pack));

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
        PackSyncClient.handleHashList("srv-hashonly.test", hashes(pack));
        PackSyncTrustStore.get().trustServer("srv-hashonly.test");
        assertNull(PackSyncClient.handleBundle(List.of(pack)).disconnect());
        assertEquals(1, ScriptPackRegistry.get().serverCachePacks().size());

        // 切到 hashOnly：bundle 被忽略
        config("hashOnly", false);
        PackSyncClient.handleHashList("srv-hashonly.test", hashes(pack));
        assertTrue(ScriptPackRegistry.get().serverCachePacks().isEmpty());
        assertEquals(2, reloads.get()); // hashOnly 清空时触发重载
        assertNull(PackSyncClient.handleBundle(List.of(pack)).disconnect());
        assertTrue(ScriptPackRegistry.get().serverCachePacks().isEmpty());

        // 空清单同样清空（幂等：已空则不再重载）
        PackSyncClient.handleHashList("srv-hashonly.test", List.of());
        assertEquals(2, reloads.get());
    }

    @Test
    void clientModeOffIgnoresEverything() {
        config("off", false);
        installCountingReloadHook();
        String manifest = "{\"id\": \"demo\", \"version\": \"1.0.0\"}";
        SyncedPack pack = pack("packs:demo", "GLOBAL", manifest, "client_scripts/hud.js", "hud()");

        PackSyncClient.handleHashList("srv-off.test", hashes(pack));
        assertNull(PackSyncClient.handleBundle(List.of(pack)).disconnect());

        assertTrue(ScriptPackRegistry.get().serverCachePacks().isEmpty());
        assertEquals(0, reloads.get());
    }

    /* ================= 辅助 ================= */

    private void config(String packSyncMode, boolean allowUnsigned) {
        ClassFilter.INSTANCE.updateConfig(new SandboxConfig(
            false, false, false, false, true, true, false, true, 30, 0, 0,
            packSyncMode, allowUnsigned, false));
    }

    private void installCountingReloadHook() {
        reloads.set(0);
        PackSyncClient.installClientReloadHook(reloads::incrementAndGet);
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
        String unsigned = "{\"id\": \"demo\", \"version\": \"1.0.0\"}";
        List<PackContentFile> files = List.of(new PackContentFile(path, content.getBytes()));
        JsonObject signature = PackSigner.sign(keyId, keyPair, syncId, scope, unsigned, files);
        JsonObject root = JsonParser.parseString(unsigned).getAsJsonObject();
        root.add("signature", signature);
        return root.toString();
    }
}
