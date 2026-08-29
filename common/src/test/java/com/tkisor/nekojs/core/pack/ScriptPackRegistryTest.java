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
 * 脚本包模型聚焦测试：manifest 宽松解析、状态文件优先级、目录扫描规则
 * （非包目录忽略 / 损坏 manifest 跳过 / id 清洗与去重 / 字母序）、WORLD 激活与卸载。
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

        assertEquals("demo", manifest.id()); // id 缺失回退目录名
        assertEquals("demo", manifest.name()); // name 缺失回退 id
        assertEquals("1.2.3", manifest.version());
        assertEquals("", manifest.description());
        assertTrue(manifest.authors().isEmpty());
        assertTrue(manifest.enabledByDefault()); // 默认启用
        assertTrue(manifest.clientSync()); // 默认随服分发（P2 消费）
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

        // 只有 on_by_state（manifest 禁用 + 状态文件启用）处于启用态；
        // off_by_manifest（manifest 禁用、无状态文件）与 off_by_state（状态文件禁用）均不参与发现。
        assertEquals(
            List.of("on_by_state"),
            registry.enabledPacks().stream().map(ScriptPack::id).toList());
        assertEquals(3, registry.globalPacks().size()); // 全部被扫描，禁用者仅不参与发现
    }

    @Test
    void scanIgnoresNonPacksSortsByIdAndDeduplicates() throws Exception {
        pack(packsRoot, "zeta", "{}");
        pack(packsRoot, "alpha", "{}");
        Files.createDirectories(packsRoot.resolve("plain_directory")); // 无 manifest：非包
        pack(packsRoot, "Alpha", "{}"); // 清洗后与 alpha 撞 id：后者跳过

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
        assertEquals("my_pack_", registry.globalPacks().get(0).id()); // 目录名与 manifest id 同源清洗
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
            registry.enabledPacks().stream().map(ScriptPack::id).toList()); // GLOBAL 在前
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
