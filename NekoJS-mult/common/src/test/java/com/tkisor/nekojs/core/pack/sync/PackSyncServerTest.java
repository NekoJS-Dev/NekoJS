package com.tkisor.nekojs.core.pack.sync;

import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.pack.ScriptPackRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 服务端 gather 测试：仅收集 enabled && clientSync 的 GLOBAL/WORLD 包，
 * syncId/哈希/manifest 原文构造正确；engine.toml 开关读取。
 */
class PackSyncServerTest {

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @TempDir
    Path packsRoot;

    @AfterEach
    void cleanup() {
        ClassFilter.INSTANCE.updateConfig(SandboxConfig.defaultConfig());
        ScriptPackRegistry.get().deactivateServerCachePacks();
    }

    @Test
    void collectGathersEnabledClientSyncPacksOnly() throws Exception {
        pack(packsRoot, "synced", "{\"id\": \"synced\", \"clientSync\": true}");
        write(packsRoot.resolve("synced"), "server_scripts/one.js", "one()");
        pack(packsRoot, "no_sync", "{\"id\": \"no_sync\", \"clientSync\": false}");
        pack(packsRoot, "disabled", "{\"id\": \"disabled\", \"enabled\": false}");
        ScriptPackRegistry.get().refreshGlobalPacks(packsRoot);

        List<SyncedPack> packs = PackSyncServer.collectSyncPacks();

        assertEquals(1, packs.size());
        SyncedPack pack = packs.get(0);
        assertEquals("packs:synced", pack.syncId());
        assertEquals("GLOBAL", pack.scopeName());
        assertEquals(
            List.of("server_scripts/one.js"),
            pack.files().stream().map(PackContentFile::relativePath).toList());
        assertEquals(
            PackHasher.hash(pack.manifestJson().getBytes(java.nio.charset.StandardCharsets.UTF_8), pack.files()),
            pack.hash());
        assertTrue(pack.manifestJson().contains("\"synced\""));
    }

    @Test
    void enabledAndModeReadFromEngineConfig() {
        assertFalse(PackSyncServer.enabled()); // 默认 off
        config("hashOnly");
        assertTrue(PackSyncServer.enabled());
        assertTrue(PackSyncServer.hashOnly());
        config("all");
        assertTrue(PackSyncServer.enabled());
        assertFalse(PackSyncServer.hashOnly());
    }

    private static void config(String packSyncMode) {
        ClassFilter.INSTANCE.updateConfig(new SandboxConfig(
            false, false, false, false, true, true, false, true, 30, 0, 0, packSyncMode, false, false));
    }

    private static void pack(Path root, String name, String manifestJson) throws Exception {
        Path dir = root.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("manifest.json"), manifestJson);
    }

    private static void write(Path packDir, String relative, String content) throws Exception {
        Path file = packDir.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
