package com.tkisor.nekojs.core.pack.sync;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 服务器包缓存落盘测试：路径穿越拒绝（{@code ../evil} 不落盘不出包目录）、
 * 落盘 → 从盘重扫 → 重算哈希与内存侧一致、盘上篡改后重算哈希变化（错配可检出）、
 * 旧文件残留清理（同 syncId 重写不残留）。
 */
class ServerPackCacheTest {

    @TempDir
    Path bucketRoot;

    private static final String MANIFEST = "{\"id\": \"demo\", \"version\": \"1.0.0\"}";
    private static final List<PackContentFile> FILES = List.of(
        new PackContentFile("client_scripts/hud.js", "hud()".getBytes()),
        new PackContentFile("assets/nekojs/lang/en_us.json", "{}".getBytes()));

    @Test
    void persistThenReloadRecomputesSameHash() {
        ServerPackCache.persistPack(bucketRoot, "packs:demo", MANIFEST, FILES);
        String expected = PackHasher.hash(MANIFEST.getBytes(), FILES);

        ServerPackCache.CachedPack cached = ServerPackCache.loadPack(bucketRoot, "packs:demo");

        assertEquals(expected, cached.hash());
        assertEquals(MANIFEST, cached.manifestJson());
        assertEquals(
            List.of("assets/nekojs/lang/en_us.json", "client_scripts/hud.js"),
            cached.files().stream().map(PackContentFile::relativePath).toList()); // 按路径排序
    }

    @Test
    void pathTraversalRejected() throws Exception {
        List<PackContentFile> evil = List.of(
            new PackContentFile("client_scripts/ok.js", "ok()".getBytes()),
            new PackContentFile("../evil.js", "PWNED".getBytes()),
            new PackContentFile("assets/../../escape.js", "PWNED2".getBytes()));

        ServerPackCache.persistPack(bucketRoot, "packs:evil", MANIFEST, evil);

        // 逃逸文件不落盘（既不在包目录内，也不在 bucket 外）
        assertFalse(Files.exists(bucketRoot.resolve("evil.js")));
        assertFalse(Files.exists(bucketRoot.getParent().resolve("evil.js")));
        assertFalse(Files.exists(bucketRoot.resolve("escape.js")));

        // 重扫只见合法文件：哈希按盘上实际内容计算（与含逃逸文件的内存快照不同）
        ServerPackCache.CachedPack cached = ServerPackCache.loadPack(bucketRoot, "packs:evil");
        assertEquals(
            List.of("client_scripts/ok.js"),
            cached.files().stream().map(PackContentFile::relativePath).toList());
        assertNotEquals(PackHasher.hash(MANIFEST.getBytes(), evil), cached.hash());
    }

    @Test
    void rehashDetectsDiskTamper() throws Exception {
        ServerPackCache.persistPack(bucketRoot, "packs:demo", MANIFEST, FILES);
        String before = ServerPackCache.loadPack(bucketRoot, "packs:demo").hash();

        Path script = bucketRoot.resolve(SyncedPack.encodeSyncId("packs:demo"))
            .resolve("client_scripts").resolve("hud.js");
        Files.writeString(script, "hudTampered()");

        String after = ServerPackCache.loadPack(bucketRoot, "packs:demo").hash();
        assertNotEquals(before, after); // 调用方对照预期哈希即可检出篡改
    }

    @Test
    void staleFilesRemovedOnRewrite() throws Exception {
        ServerPackCache.persistPack(bucketRoot, "packs:demo", MANIFEST, FILES);
        Path packDir = bucketRoot.resolve(SyncedPack.encodeSyncId("packs:demo"));
        Files.writeString(packDir.resolve("client_scripts").resolve("extra.js"), "stale");

        // 同步到新版（少一个文件）：旧目录整删重建，extra.js 不残留
        List<PackContentFile> v2 = List.of(new PackContentFile("client_scripts/hud.js", "hud()".getBytes()));
        ServerPackCache.persistPack(bucketRoot, "packs:demo", MANIFEST, v2);
        assertFalse(Files.exists(packDir.resolve("client_scripts").resolve("extra.js")));
        assertFalse(Files.exists(packDir.resolve("assets")));
    }

    @Test
    void missingDirectoryLoadsNull() {
        assertNull(ServerPackCache.loadPack(bucketRoot, "packs:absent"));
    }

    @Test
    void syncIdEncodingIsSafeAndDeterministic() {
        assertEquals("packs_demo", SyncedPack.encodeSyncId("packs:demo"));
        assertEquals("worldpacks_demo", SyncedPack.encodeSyncId("worldpacks:demo"));
        assertEquals("packs_demo", SyncedPack.encodeSyncId(SyncedPack.encodeSyncId("packs:demo")));
        assertTrue(SyncedPack.encodeSyncId("worldpacks:demo").startsWith("worldpacks_"));
    }
}
