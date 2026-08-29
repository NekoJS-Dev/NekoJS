package com.tkisor.nekojs.core.pack.sync;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.pack.ScriptPack;
import com.tkisor.nekojs.core.pack.ScriptPackRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @AfterEach
    void cleanup() {
        ClassFilter.INSTANCE.updateConfig(SandboxConfig.defaultConfig());
        PackSyncClient.installClientReloadHook(null);
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
        KeyPair keyPair = PackSigner.generateKeyPair();
        String unsigned = "{\"id\": \"demo\", \"version\": \"1.0.0\"}";
        List<PackContentFile> files = List.of(new PackContentFile("client_scripts/hud.js", "hud()".getBytes()));
        JsonObject signature = PackSigner.sign(keyId, keyPair, syncId, scope, unsigned, files);
        JsonObject root = JsonParser.parseString(unsigned).getAsJsonObject();
        root.add("signature", signature);
        return root.toString();
    }
}
