package com.tkisor.nekojs.core.pack.sync;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Server pack cache write tests: path traversal is rejected ({@code ../evil} is not written and
 * does not leave the pack directory), a write followed by a rescan from disk gives the same hash
 * as the in-memory data, tampering on disk changes the recomputed hash so a mismatch can be
 * detected, and stale files are removed when the same syncId is rewritten.
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
            cached.files().stream().map(PackContentFile::relativePath).toList()); // Sorted by path.
    }

    @Test
    void pathTraversalRejected() throws Exception {
        List<PackContentFile> evil = List.of(
            new PackContentFile("client_scripts/ok.js", "ok()".getBytes()),
            new PackContentFile("../evil.js", "PWNED".getBytes()),
            new PackContentFile("assets/../../escape.js", "PWNED2".getBytes()));

        ServerPackCache.persistPack(bucketRoot, "packs:evil", MANIFEST, evil);

        // Escaping files are not written: nothing lands inside the pack directory or outside the bucket.
        assertFalse(Files.exists(bucketRoot.resolve("evil.js")));
        assertFalse(Files.exists(bucketRoot.getParent().resolve("evil.js")));
        assertFalse(Files.exists(bucketRoot.resolve("escape.js")));

        // The rescan sees only the legal file, so the hash comes from what is on disk, unlike the
        // in-memory snapshot that still holds the escaping files.
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
        assertNotEquals(before, after); // The caller detects tampering by comparing against the expected hash.
    }

    @Test
    void staleFilesRemovedOnRewrite() throws Exception {
        ServerPackCache.persistPack(bucketRoot, "packs:demo", MANIFEST, FILES);
        Path packDir = bucketRoot.resolve(SyncedPack.encodeSyncId("packs:demo"));
        Files.writeString(packDir.resolve("client_scripts").resolve("extra.js"), "stale");

        // Sync a new version with one file fewer: the old directory is deleted and rebuilt, so extra.js
        // does not survive.
        List<PackContentFile> v2 = List.of(new PackContentFile("client_scripts/hud.js", "hud()".getBytes()));
        ServerPackCache.persistPack(bucketRoot, "packs:demo", MANIFEST, v2);
        assertFalse(Files.exists(packDir.resolve("client_scripts").resolve("extra.js")));
        assertFalse(Files.exists(packDir.resolve("assets")));
    }

    @Test
    void physicalRestorePropagatesDeleteFailureAndRetainsStaging() throws Exception {
        String syncId = "packs:restore-failure";
        ServerPackCache.persistPack(bucketRoot, syncId, MANIFEST, FILES);
        Path staging = ServerPackCache.createStagingRoot(bucketRoot);
        ServerPackCache.stagePack(staging, syncId, MANIFEST, FILES);
        Path target = bucketRoot.resolve(SyncedPack.encodeSyncId(syncId));

        ServerPackCache.PhysicalReplacement replacement = new ServerPackCache.PhysicalReplacement(
                staging, bucketRoot, path -> {
                    if (path.equals(target)) {
                        throw new IOException("injected physical delete failure");
                    }
                    ServerPackCache.deleteRecursivelyStrict(path);
                });
        replacement.replace(syncId);

        assertThrows(IOException.class, replacement::restore,
                "physical rollback must not swallow a failed target delete");
        assertTrue(Files.exists(staging), "failed physical rollback must not clear staging evidence");
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
