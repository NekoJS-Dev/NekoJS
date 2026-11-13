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
 * Trust store tests: a trusted bucket is still there after the store is rebuilt (persistence),
 * addresses are normalized, signing keys are added and looked up by pinning, and a corrupt file
 * degrades to an empty store.
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

        // A rebuilt instance (a game restart) reads the trust back from disk.
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

        // Overwrite: the same keyId gets a new key.
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

        // After the fallback the store still accepts new trust and overwrites the corrupt file.
        store.trustServer("play.example.com");
        assertTrue(new PackSyncTrustStore(file).isServerTrusted(PackSyncTrustStore.bucketFor("play.example.com")));
    }
}
