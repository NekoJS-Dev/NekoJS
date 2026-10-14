package com.tkisor.nekojs.core.pack;

import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScriptPackOperationsTest {
    @TempDir
    Path root;

    @BeforeAll
    static void init() {
        TestPlatformInit.ensureInitialized();
    }

    @Test
    void ambiguousIdCannotChangeEitherSourceButQualifiedSelectorCan() throws Exception {
        Path global = pack(root.resolve("global"), "demo");
        Path world = pack(root.resolve("world").resolve(ScriptPackRegistry.WORLD_PACKS_DIR), "demo");
        ScriptPackRegistry registry = registry();
        registry.activateWorldPacks(root.resolve("world"));

        var ambiguous = registry.setEnabled("demo", false);
        assertEquals(PackOperationResult.Status.AMBIGUOUS, ambiguous.status());
        assertEquals(List.of("GLOBAL:demo", "WORLD:demo"), ambiguous.matches());
        assertFalse(Files.exists(global.resolve(ScriptPackState.FILE_NAME)));
        assertFalse(Files.exists(world.resolve(ScriptPackState.FILE_NAME)));

        assertTrue(registry.setEnabled("world:demo", false).succeeded());
        assertTrue(registry.globalPacks().getFirst().enabled());
        assertFalse(registry.worldPacks().getFirst().enabled());
        assertFalse(ScriptPackState.load(world).enabled());
    }

    @Test
    void uniqueLegacyIdWorksAndMissingSelectorFails() throws Exception {
        pack(root.resolve("global"), "demo");
        ScriptPackRegistry registry = registry();
        assertTrue(registry.setEnabled("demo", false).succeeded());
        assertFalse(registry.globalPacks().getFirst().enabled());
        assertEquals(PackOperationResult.Status.NOT_FOUND, registry.setEnabled("WORLD:demo", true).status());
    }

    @Test
    void saveFailureNeverReportsSuccessOrChangesSnapshot() throws Exception {
        Path pack = pack(root.resolve("global"), "demo");
        Path state = Files.createDirectory(pack.resolve(ScriptPackState.FILE_NAME));
        ScriptPackRegistry registry = registry();
        var result = registry.setEnabled("demo", false);
        assertEquals(PackOperationResult.Status.SAVE_FAILED, result.status());
        assertFalse(result.succeeded());
        assertTrue(registry.globalPacks().getFirst().enabled());
        assertTrue(Files.isDirectory(state));
        try (var paths = Files.list(pack)) {
            assertEquals(2, paths.count());
        }
    }

    @Test
    void malformedStateIsRetainedInsteadOfOverwrittenWithDefaults() throws Exception {
        Path pack = pack(root.resolve("global"), "demo");
        Path state = pack.resolve(ScriptPackState.FILE_NAME);
        Files.writeString(state, "{ broken");
        assertEquals(PackOperationResult.Status.SAVE_FAILED, registry().setEnabled("demo", false).status());
        assertEquals("{ broken", Files.readString(state));
    }

    @Test
    void validStateIsReplacedAndNoTemporaryFileRemains() throws Exception {
        Path pack = pack(root.resolve("global"), "demo");
        assertTrue(ScriptPackState.save(pack, false));
        assertTrue(ScriptPackState.save(pack, true));
        assertTrue(ScriptPackState.load(pack).enabled());
        try (var paths = Files.list(pack)) {
            assertEquals(2, paths.count());
        }
    }

    @Test
    void remotePacksAreListedButNeverLocallyRewrittenOrRescanned() throws Exception {
        Path remote = pack(root.resolve("bucket"), "demo");
        ScriptPackRegistry registry = registry();
        registry.activateServerCachePacks(root.resolve("bucket"));
        Files.writeString(remote.resolve(ScriptPackManifest.FILE_NAME), "{broken");
        var result = registry.setEnabled("SERVER_CACHE:demo", false);
        assertEquals(PackOperationResult.Status.READ_ONLY, result.status());
        assertEquals(List.of("SERVER_CACHE:demo"), registry.allPacks().stream().map(ScriptPack::selector).toList());
        assertTrue(registry.serverCachePacks().getFirst().enabled());
        assertFalse(Files.exists(remote.resolve(ScriptPackState.FILE_NAME)));
    }

    @Test
    void refreshObservesCurrentWorldEditsButCannotResurrectAnUnmountedWorld() throws Exception {
        Path worldRoot = root.resolve("world");
        Path world = pack(worldRoot.resolve(ScriptPackRegistry.WORLD_PACKS_DIR), "demo");
        ScriptPackRegistry registry = registry();
        registry.activateWorldPacks(worldRoot);
        assertTrue(ScriptPackState.save(world, false));
        pack(worldRoot.resolve(ScriptPackRegistry.WORLD_PACKS_DIR), "new_pack");
        registry.refreshLocalPacks();
        assertEquals(List.of("new_pack"), registry.enabledPacks().stream().map(ScriptPack::id).toList());
        registry.deactivateWorldPacks();
        registry.refreshLocalPacks();
        assertTrue(registry.worldPacks().isEmpty());
    }

    private ScriptPackRegistry registry() {
        ScriptPackRegistry registry = new ScriptPackRegistry();
        registry.refreshGlobalPacks(root.resolve("global"));
        return registry;
    }

    private static Path pack(Path root, String id) throws Exception {
        Path pack = Files.createDirectories(root.resolve(id));
        Files.writeString(pack.resolve(ScriptPackManifest.FILE_NAME), "{\"id\":\"" + id + "\"}");
        return pack;
    }
}
