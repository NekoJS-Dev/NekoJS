package com.tkisor.nekojs.core.pack;

import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for the script pack model: lenient manifest parsing, state file precedence, directory
 * scan rules (non-pack directories ignored, broken manifests skipped, id sanitizing and
 * deduplication, alphabetical order), and WORLD activation and unload.
 */
class ScriptPackRegistryTest {

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @TempDir
    Path packsRoot;

    @TempDir
    Path worldRoot;

    @Test
    void manifestMissingFieldsFallBackToDefaults() throws Exception {
        Path dir = pack(packsRoot, "demo", "{\"version\": \"1.2.3\"}");
        ScriptPackManifest manifest = ScriptPackManifest.load(dir, "demo");

        assertEquals("demo", manifest.id()); // A missing id falls back to the directory name.
        assertEquals("demo", manifest.name()); // A missing name falls back to the id.
        assertEquals("1.2.3", manifest.version());
        assertEquals("", manifest.description());
        assertTrue(manifest.authors().isEmpty());
        assertTrue(manifest.enabledByDefault()); // Enabled by default.
        assertTrue(manifest.clientSync()); // Shipped with the server by default
        assertNull(manifest.signature());
    }

    @Test
    void manifestAbsentOrCorruptMeansNotAPack() throws Exception {
        Path noManifest = Files.createDirectories(packsRoot.resolve("not_a_pack"));
        assertNull(ScriptPackManifest.load(noManifest, "not_a_pack"));

        Path corrupt = pack(packsRoot, "corrupt", "{ not json");
        assertNull(ScriptPackManifest.load(corrupt, "corrupt"));
    }

    @Test
    void stateFileOverridesManifestDefault() throws Exception {
        pack(packsRoot, "off_by_manifest", "{\"enabled\": false}");
        pack(packsRoot, "off_by_state", "{\"enabled\": true}");
        Files.writeString(packsRoot.resolve("off_by_state").resolve(ScriptPackState.FILE_NAME), "{\"enabled\": false}");
        pack(packsRoot, "on_by_state", "{\"enabled\": false}");
        Files.writeString(packsRoot.resolve("on_by_state").resolve(ScriptPackState.FILE_NAME), "{\"enabled\": true}");

        ScriptPackRegistry registry = new ScriptPackRegistry();
        registry.refreshGlobalPacks(packsRoot);

        // Only on_by_state is enabled: the manifest disables it and the state file enables it.
        // off_by_manifest (manifest disabled, no state file) and off_by_state (state file
        // disabled) are not discovered.
        assertEquals(
            List.of("on_by_state"),
            registry.enabledPacks().stream().map(ScriptPack::id).toList());
        assertEquals(3, registry.globalPacks().size()); // All packs are scanned; a disabled pack is only left out of discovery.
    }

    @Test
    void scanIgnoresNonPacksSortsByIdAndDeduplicates() throws Exception {
        pack(packsRoot, "zeta", "{}");
        pack(packsRoot, "alpha", "{}");
        Files.createDirectories(packsRoot.resolve("plain_directory")); // No manifest: not a pack.
        pack(packsRoot, "Alpha", "{}"); // Collides with alpha after id cleaning, so the later one is skipped.

        ScriptPackRegistry registry = new ScriptPackRegistry();
        registry.refreshGlobalPacks(packsRoot);

        assertEquals(List.of("alpha", "zeta"), registry.globalPacks().stream().map(ScriptPack::id).toList());
    }

    @Test
    void invalidIdIsSanitizedToDirectoryFallback() throws Exception {
        pack(packsRoot, "My Pack!", "{\"id\": \"My Pack!\"}");

        ScriptPackRegistry registry = new ScriptPackRegistry();
        registry.refreshGlobalPacks(packsRoot);

        assertEquals(1, registry.globalPacks().size());
        assertEquals("my_pack_", registry.globalPacks().get(0).id()); // Directory names and manifest ids are cleaned the same way.
    }

    @Test
    void worldPacksActivateAndDeactivateIndependently() throws Exception {
        pack(packsRoot, "global_pack", "{}");
        ScriptPackRegistry registry = new ScriptPackRegistry();
        registry.refreshGlobalPacks(packsRoot);

        pack(worldRoot.resolve(ScriptPackRegistry.WORLD_PACKS_DIR), "world_pack", "{}");
        List<ScriptPack> activated = registry.activateWorldPacks(worldRoot);

        assertEquals(List.of("world_pack"), activated.stream().map(ScriptPack::id).toList());
        assertEquals(
            List.of("global_pack", "world_pack"),
            registry.enabledPacks().stream().map(ScriptPack::id).toList()); // GLOBAL packs come first.
        assertEquals(ScriptPackScope.WORLD, registry.worldPacks().get(0).scope());

        List<ScriptPack> removed = registry.deactivateWorldPacks();
        assertEquals(1, removed.size());
        assertTrue(registry.worldPacks().isEmpty());
        assertEquals(List.of("global_pack"), registry.enabledPacks().stream().map(ScriptPack::id).toList());
    }

    @Test
    void idPathPrefixEncodesScopeForListenerCleanup() throws Exception {
        pack(packsRoot, "gp", "{}");
        Path worldPacks = worldRoot.resolve(ScriptPackRegistry.WORLD_PACKS_DIR);
        pack(worldPacks, "wp", "{}");
        ScriptPackRegistry registry = new ScriptPackRegistry();
        registry.refreshGlobalPacks(packsRoot);
        registry.activateWorldPacks(worldRoot);

        ScriptPack global = registry.globalPacks().get(0);
        ScriptPack world = registry.worldPacks().get(0);

        assertEquals("packs/gp/", global.idPathPrefix());
        assertEquals("worldpacks/wp/", world.idPathPrefix());
        assertEquals("nekojs:server/packs/gp/", global.scriptIdPrefix(com.tkisor.nekojs.api.ScriptType.SERVER));
        assertEquals("nekojs:server/worldpacks/wp/", world.scriptIdPrefix(com.tkisor.nekojs.api.ScriptType.SERVER));
        assertEquals(
            packsRoot.resolve("gp").resolve("server_scripts"),
            global.scriptsDirFor(com.tkisor.nekojs.api.ScriptType.SERVER));
    }

    @Test
    void missingRootsScanToEmpty() {
        ScriptPackRegistry registry = new ScriptPackRegistry();
        registry.refreshGlobalPacks(packsRoot.resolve("does_not_exist"));
        assertTrue(registry.globalPacks().isEmpty());
        assertTrue(registry.activateWorldPacks(null).isEmpty());
        assertTrue(registry.enabledPacks().isEmpty());
    }

    private static Path pack(Path root, String name, String manifestJson) throws Exception {
        Path dir = root.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(ScriptPackManifest.FILE_NAME), manifestJson);
        return dir;
    }
}
