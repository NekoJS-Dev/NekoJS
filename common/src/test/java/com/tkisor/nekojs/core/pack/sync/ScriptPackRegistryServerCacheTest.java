package com.tkisor.nekojs.core.pack.sync;

import com.tkisor.nekojs.core.pack.ScriptPack;
import com.tkisor.nekojs.core.pack.ScriptPackRegistry;
import com.tkisor.nekojs.core.pack.ScriptPackScope;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SERVER_CACHE 作用域激活/卸载测试：强制启用（manifest 默认值与状态文件均不适用）、
 * 前缀段 {@code serverpacks/<id>/}、与 GLOBAL/WORLD 并入 enabledPacks 的顺序。
 */
class ScriptPackRegistryServerCacheTest {

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @TempDir
    Path globalRoot;

    @TempDir
    Path bucketDir;

    @AfterEach
    void cleanup() {
        ScriptPackRegistry.get().deactivateServerCachePacks();
    }

    @Test
    void serverCachePacksAreForceEnabled() throws Exception {
        // manifest enabled=false + 状态文件 disabled：服务器缓存包仍强制启用
        Path pack = pack(bucketDir, "remote_pack", "{\"id\": \"remote_pack\", \"enabled\": false}");
        Files.writeString(pack.resolve(".neko_pack.state.json"), "{\"enabled\": false}");

        List<ScriptPack> activated = ScriptPackRegistry.get().activateServerCachePacks(bucketDir);

        assertEquals(1, activated.size());
        assertTrue(activated.get(0).enabled());
        assertEquals(ScriptPackScope.SERVER_CACHE, activated.get(0).scope());
        assertEquals("serverpacks/remote_pack/", activated.get(0).idPathPrefix());
        assertEquals(
            "nekojs:client/serverpacks/remote_pack/",
            activated.get(0).scriptIdPrefix(ScriptType.CLIENT));
    }

    @Test
    void serverCachePacksJoinEnabledPacksAfterWorld() throws Exception {
        pack(globalRoot, "g_pack", "{\"id\": \"g_pack\"}");
        pack(bucketDir, "s_pack", "{\"id\": \"s_pack\"}");

        ScriptPackRegistry registry = new ScriptPackRegistry();
        registry.refreshGlobalPacks(globalRoot);
        registry.activateServerCachePacks(bucketDir);

        assertEquals(
            List.of("g_pack", "s_pack"),
            registry.enabledPacks().stream().map(ScriptPack::id).toList()); // GLOBAL 在前、SERVER_CACHE 在后
    }

    @Test
    void deactivateClearsServerCacheScope() throws Exception {
        pack(bucketDir, "s_pack", "{\"id\": \"s_pack\"}");
        ScriptPackRegistry registry = new ScriptPackRegistry();
        registry.activateServerCachePacks(bucketDir);

        List<ScriptPack> removed = registry.deactivateServerCachePacks();

        assertEquals(1, removed.size());
        assertTrue(registry.serverCachePacks().isEmpty());
        assertTrue(registry.enabledPacks().isEmpty());
    }

    private static Path pack(Path root, String name, String manifestJson) throws Exception {
        Path dir = root.resolve(name);
        Files.createDirectories(dir.resolve("client_scripts"));
        Files.writeString(dir.resolve("manifest.json"), manifestJson);
        return dir;
    }
}
