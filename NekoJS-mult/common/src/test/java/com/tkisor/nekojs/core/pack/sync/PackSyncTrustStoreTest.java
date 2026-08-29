package com.tkisor.nekojs.core.pack.sync;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 信任存储测试：bucket 信任写入后重建实例可读回（持久化）、地址规范化、
 * 签名公钥 pinning 增查、损坏文件按空存储降级。
 */
class PackSyncTrustStoreTest {

    @TempDir
    Path dir;

    @Test
    void serverTrustPersistsAcrossReload() {
        Path file = dir.resolve("trusted-servers.json");
        PackSyncTrustStore store = new PackSyncTrustStore(file);
        String bucket = PackSyncTrustStore.bucketFor("play.example.com");

        assertFalse(store.isServerTrusted(bucket));
        store.trustServer("play.example.com");
        assertTrue(store.isServerTrusted(bucket));

        // 重建实例（= 重启游戏）从盘读回
        PackSyncTrustStore reloaded = new PackSyncTrustStore(file);
        assertTrue(reloaded.isServerTrusted(bucket));
        assertTrue(Files.isRegularFile(file));
    }

    @Test
    void bucketIsCaseAndWhitespaceInsensitive() {
        assertEquals(
            PackSyncTrustStore.bucketFor("play.example.com"),
            PackSyncTrustStore.bucketFor("  PLAY.Example.COM "));
        assertFalse(PackSyncTrustStore.bucketFor("a.com").equals(PackSyncTrustStore.bucketFor("b.com")));
    }

    @Test
    void keyPinningAddAndQuery() {
        PackSyncTrustStore store = new PackSyncTrustStore(dir.resolve("keys.json"));

        assertNull(store.trustedPublicKey("author-key"));
        store.trustPublicKey("author-key", "KEYDATA", "aa:bb", "play.example.com");

        assertEquals("KEYDATA", store.trustedPublicKey("author-key"));
        assertNull(store.trustedPublicKey("other-key"));

        // 覆盖写（同 keyId 换钥）
        store.trustPublicKey("author-key", "ROTATED", "cc:dd", "play.example.com");
        assertEquals("ROTATED", store.trustedPublicKey("author-key"));
    }

    @Test
    void corruptFileDegradesToEmptyStore() throws Exception {
        Path file = dir.resolve("corrupt.json");
        Files.writeString(file, "{ not json !!");
        PackSyncTrustStore store = new PackSyncTrustStore(file);

        assertFalse(store.isServerTrusted("whatever"));
        assertNull(store.trustedPublicKey("whatever"));

        // 降级后仍可写入新信任（覆盖损坏文件）
        store.trustServer("play.example.com");
        assertTrue(new PackSyncTrustStore(file).isServerTrusted(PackSyncTrustStore.bucketFor("play.example.com")));
    }
}
