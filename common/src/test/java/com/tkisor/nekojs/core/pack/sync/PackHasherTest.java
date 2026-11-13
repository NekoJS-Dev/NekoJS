package com.tkisor.nekojs.core.pack.sync;

import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pack hash tests: the hash is stable whatever order the files were created in, it changes when
 * the content or the manifest changes, and only content directories count (scripts/assets/data
 * take part, state files do not).
 */
class PackHasherTest {

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @TempDir
    Path packRoot;

    @Test
    void hashIsStableAcrossFileCreationOrder() throws Exception {
        Path a = buildPack("pack_a", true); // Writes one.js before two.js.
        Path b = buildPack("pack_b", false); // Writes two.js before one.js; the content is the same.

        assertEquals(PackHasher.hashPackDir(a), PackHasher.hashPackDir(b));
    }

    @Test
    void hashChangesOnContentChange() throws Exception {
        Path base = buildPack("base", true);
        String before = PackHasher.hashPackDir(base);

        Files.writeString(base.resolve("server_scripts").resolve("one.js"), "helloModified");
        assertNotEquals(before, PackHasher.hashPackDir(base));
    }

    @Test
    void hashChangesOnManifestChange() throws Exception {
        Path base = buildPack("base_m", true);
        String before = PackHasher.hashPackDir(base);

        Files.writeString(base.resolve("manifest.json"), "{\"id\": \"base_m\", \"version\": \"2.0.0\"}");
        assertNotEquals(before, PackHasher.hashPackDir(base));
    }

    @Test
    void contentDirsAndStateFiles() throws Exception {
        Path pack = packRoot.resolve("scope_pack");
        Files.createDirectories(pack.resolve("client_scripts"));
        Files.createDirectories(pack.resolve("assets/nekojs/textures"));
        Files.createDirectories(pack.resolve("data/nekojs/recipes"));
        Files.createDirectories(pack.resolve("ignored_dir"));
        Files.writeString(pack.resolve("manifest.json"), "{\"id\": \"scope_pack\"}");
        Files.writeString(pack.resolve("client_scripts").resolve("hud.js"), "hud()");
        Files.writeString(pack.resolve("assets/nekojs/textures").resolve("logo.png"), "png");
        Files.writeString(pack.resolve("data/nekojs/recipes").resolve("r.json"), "{}");
        Files.writeString(pack.resolve("ignored_dir").resolve("misc.txt"), "not packed");
        Files.writeString(pack.resolve(".neko_pack.state.json"), "{\"enabled\": true}");

        List<PackContentFile> files = PackHasher.readContentFiles(pack);
        assertEquals(
            List.of("assets/nekojs/textures/logo.png", "client_scripts/hud.js", "data/nekojs/recipes/r.json"),
            files.stream().map(PackContentFile::relativePath).toList()); // Sorted and filtered by scope: state files and unknown directories take no part.

        // The hash covers the manifest plus the sorted content files, so build it by hand here.
        String expected = PackHasher.hash(
            Files.readAllBytes(pack.resolve("manifest.json")),
            List.of(
                new PackContentFile("assets/nekojs/textures/logo.png", "png".getBytes()),
                new PackContentFile("client_scripts/hud.js", "hud()".getBytes()),
                new PackContentFile("data/nekojs/recipes/r.json", "{}".getBytes())));
        assertEquals(expected, PackHasher.hashPackDir(pack));

        // Flipping the state file does not change the hash; it is not distributed content.
        Files.writeString(pack.resolve(".neko_pack.state.json"), "{\"enabled\": false}");
        assertEquals(expected, PackHasher.hashPackDir(pack));
    }

    @Test
    void missingManifestIsRejected() throws Exception {
        Path noManifest = Files.createDirectories(packRoot.resolve("not_a_pack"));
        try {
            PackHasher.hashPackDir(noManifest);
            org.junit.jupiter.api.Assertions.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(true);
        }
    }

    /** Builds a pack: a manifest (both packs use the same id, since the manifest bytes take part in
     * the hash and the directory name does not) plus two server_scripts files
     * (one.js=hello, two.js=second, written in either order). */
    private Path buildPack(String id, boolean oneFirst) throws Exception {
        Path pack = packRoot.resolve(id);
        Files.createDirectories(pack.resolve("server_scripts"));
        Files.writeString(pack.resolve("manifest.json"), "{\"id\": \"same_id\"}");
        if (oneFirst) {
            Files.writeString(pack.resolve("server_scripts").resolve("one.js"), "hello");
            Files.writeString(pack.resolve("server_scripts").resolve("two.js"), "second");
        } else {
            Files.writeString(pack.resolve("server_scripts").resolve("two.js"), "second");
            Files.writeString(pack.resolve("server_scripts").resolve("one.js"), "hello");
        }
        return pack;
    }
}
