package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.SourceMapRegistry;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.fs.ScriptPathProvider;
import com.tkisor.nekojs.core.module.NekoEsmVirtualModuleRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * runtime-owned prepared 缓存按 {@link ScriptType} 分区的回归测试（直接测缓存，不经 Graal Context）。
 *
 * <p>背景缺陷：prepared 缓存曾是进程级静态缓存且无 ScriptType 维度，单机 CLIENT 触发 reload
 * 会误清 SERVER 等其它类型已编译的模块。修复后 {@code clear(ScriptType)} 按类型局部清除，
 * 本测试直接验证独立性，并验证跨类型共享条目（node_modules / 裸包名 moduleId）不受影响。
 *
 * <p>prepared entries、source maps 与 native ESM sources 都由本实例持有；清理边界通过
 * 同一 owner 与独立 owner 的可观察结果验证。
 */
class ScriptTypeScopedCacheClearTest {

    @BeforeAll
    static void bindPaths() {
        TestPlatformInit.ensureInitialized();
    }

    private NekoModulePipelineCache cache;

    @TempDir
    Path providerTemp;

    @BeforeEach
    void newCache() {
        ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        cache = new NekoModulePipelineCache(
                new NekoModulePipeline(new NekoCompilationPipeline(), compilers, SandboxConfig.defaultConfig()),
                new SourceMapRegistry(NekoJSPaths.get().root()),
                new NekoEsmVirtualModuleRegistry(NekoJSPaths.get().root()), NekoTrustContext.local());
    }

    @AfterEach
    void clearCaches() {
        if (cache != null) {
            cache.clear();
        }
    }

    // ---- NekoModulePipelineCache ----

    @Test
    void pipelineCacheClearByTypeOnlyRemovesThatType() throws Exception {
        Path root = NekoJSPaths.get().root().toAbsolutePath().normalize();
        Path serverKey = root.resolve("server_scripts/cache_clear/helper.cjs");
        Path clientKey = root.resolve("client_scripts/cache_clear/helper.cjs");
        Path sharedKey = root.resolve("node_modules/cache_clear/index.cjs");
        writeModule(serverKey);
        writeModule(clientKey);
        writeModule(sharedKey);
        NekoPreparedModule serverBefore = cache.prepare(serverKey);
        NekoPreparedModule clientBefore = cache.prepare(clientKey);
        NekoPreparedModule sharedBefore = cache.prepare(sharedKey);

        cache.clear(ScriptType.SERVER);

        assertNotSame(serverBefore, cache.prepare(serverKey), "SERVER 条目必须重新准备");
        assertSame(clientBefore, cache.prepare(clientKey), "CLIENT 条目必须保留");
        assertSame(sharedBefore, cache.prepare(sharedKey), "跨类型 node_modules 条目必须保留");
        Files.deleteIfExists(serverKey);
        Files.deleteIfExists(clientKey);
        Files.deleteIfExists(sharedKey);
    }

    @Test
    void packagePathsUseInjectedProviderCaseSemanticsForAllRegistries() throws Exception {
        Path archive = providerTemp.resolve("case-sensitive.zip");
        try (FileSystem fileSystem = FileSystems.newFileSystem(
                URI.create("jar:" + archive.toUri()), Map.of("create", "true"))) {
            Path root = fileSystem.getPath("/nekojs");
            Files.createDirectories(root);
            assertNull(ScriptPathProvider.typeOfSegment(fileSystem.getPath("Server_scripts")),
                    "ZIP provider must not fold script directory case");
            assertEquals(ScriptType.SERVER,
                    ScriptPathProvider.typeOfSegment(fileSystem.getPath("server_scripts")));

            Path packageServer = root.resolve("packs/id/server_scripts/server.cjs");
            Path worldServer = root.resolve("nekojs_packs/id/server_scripts/world.cjs");
            Path remoteServer = root.resolve("server_packs/bucket/id/server_scripts/remote.cjs");
            Path upperServer = root.resolve("Server_scripts/upper.cjs");
            for (Path path : List.of(packageServer, worldServer, remoteServer, upperServer)) {
                Files.createDirectories(path.getParent());
                Files.writeString(path, "module.exports = 'package';\n");
            }

            ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
            NekoModulePipelineCache providerCache = new NekoModulePipelineCache(
                    new NekoModulePipeline(new NekoCompilationPipeline(), compilers,
                            SandboxConfig.defaultConfig()),
                    new SourceMapRegistry(root), new NekoEsmVirtualModuleRegistry(root),
                    NekoTrustContext.local());
            try {
                NekoPreparedModule packagePrepared = providerCache.prepare(packageServer);
                NekoPreparedModule worldPrepared = providerCache.prepare(worldServer);
                NekoPreparedModule remotePrepared = providerCache.prepare(remoteServer);
                NekoPreparedModule upperPrepared = providerCache.prepare(upperServer);

                providerCache.sourceMaps().register("packs/id/server_scripts/map.ts",
                        minimalMap("packs/id/server_scripts/map.ts"));
                providerCache.sourceMaps().register("nekojs_packs/id/server_scripts/world-map.ts",
                        minimalMap("nekojs_packs/id/server_scripts/world-map.ts"));
                providerCache.sourceMaps().register("server_packs/bucket/id/server_scripts/remote-map.ts",
                        minimalMap("server_packs/bucket/id/server-map.ts"));
                providerCache.virtualModules().register("packs/id/server_scripts/virtual.mjs", "export default 1");
                providerCache.virtualModules().register("nekojs_packs/id/server_scripts/world.mjs", "export default 2");
                providerCache.virtualModules().register("server_packs/bucket/id/server_scripts/remote.mjs", "export default 3");
                providerCache.virtualModules().register("Server_scripts/upper.mjs", "export default 4");

                providerCache.clear(ScriptType.SERVER);

                assertNotSame(packagePrepared, providerCache.prepare(packageServer));
                assertNotSame(worldPrepared, providerCache.prepare(worldServer));
                assertNotSame(remotePrepared, providerCache.prepare(remoteServer));
                assertSame(upperPrepared, providerCache.prepare(upperServer),
                        "case-sensitive providers must not classify Server_scripts as SERVER");
                assertNull(providerCache.sourceMaps().getMappedPosition(
                        "packs/id/server_scripts/map.ts", 1, 0).path);
                assertNull(providerCache.sourceMaps().getMappedPosition(
                        "nekojs_packs/id/server_scripts/world-map.ts", 1, 0).path);
                assertNull(providerCache.sourceMaps().getMappedPosition(
                        "server_packs/bucket/id/server_scripts/remote-map.ts", 1, 0).path);
                assertTrue(isVirtual(providerCache.virtualModules(),
                        "Server_scripts/upper.mjs"), "the case-mismatched virtual module must remain");
            } finally {
                providerCache.close();
            }
        }
    }

    @Test
    void defaultProviderUsesItsOwnCaseSemantics() {
        boolean caseInsensitive = Path.of("A").getFileSystem().getPath("A")
                .equals(Path.of("a").getFileSystem().getPath("a"));
        ScriptType expected = caseInsensitive ? ScriptType.SERVER : null;
        assertEquals(expected, ScriptType.fromScriptsDirectoryName("Server_scripts"));
        assertEquals(expected, ScriptPathProvider.typeOfSegment(Path.of("Server_scripts")));
    }

    @Test
    void pipelineCacheNoArgClearWipesEverything() throws Exception {
        Path root = NekoJSPaths.get().root().toAbsolutePath().normalize();
        Path server = root.resolve("server_scripts/cache_clear_all/a.cjs");
        Path shared = root.resolve("node_modules/cache_clear_all/index.cjs");
        writeModule(server);
        writeModule(shared);
        NekoPreparedModule serverBefore = cache.prepare(server);
        cache.prepare(shared);

        cache.clear();

        assertNotSame(serverBefore, cache.prepare(server), "无参 clear 必须清空本实例全部条目");
        Files.deleteIfExists(server);
        Files.deleteIfExists(shared);
    }

    @Test
    void pipelineCacheInstancesAreIsolatedAcrossOwners() throws Exception {
            // runtime-owned 语义：两个 owner 的缓存实例互不可见；一方 clear 不影响另一方。
            NekoModulePipelineCache other = new NekoModulePipelineCache(
                new NekoModulePipeline(new NekoCompilationPipeline(), ScriptCompilerRegistry.createRuntimeRegistry(),
                        SandboxConfig.defaultConfig()), new SourceMapRegistry(NekoJSPaths.get().root()),
                new NekoEsmVirtualModuleRegistry(NekoJSPaths.get().root()), NekoTrustContext.local());
        try {
            Path serverKey = NekoJSPaths.get().root().resolve("server_scripts/cache_isolated/isolated.cjs");
            writeModule(serverKey);
            NekoPreparedModule prepared = cache.prepare(serverKey);

            assertNotSame(prepared, other.prepare(serverKey), "另一 owner 必须独立准备自己的条目");

            other.clear();
            assertSame(prepared, cache.prepare(serverKey), "另一实例全清不得影响本实例");
            Files.deleteIfExists(serverKey);
        } finally {
            other.clear();
        }
    }

    // ---- SourceMapRegistry ----

    @Test
    void sourceMapClearByTypeOnlyRemovesThatType() {
        SourceMapRegistry maps = cache.sourceMaps();
        maps.register("server_scripts/foo.ts", minimalMap("server_scripts/foo.ts"));
        maps.register("client_scripts/bar.ts", minimalMap("client_scripts/bar.ts"));

        maps.clearByScriptType(ScriptType.SERVER);

        assertNull(maps.getMappedPosition("server_scripts/foo.ts", 1, 0).path,
                "SERVER 类型 source map 必须被清除");
        assertNotNull(maps.getMappedPosition("client_scripts/bar.ts", 1, 0).path,
                "CLIENT 类型 source map 必须保留");
    }

    // ---- NekoEsmVirtualModuleRegistry ----

    @Test
    void esmRegistryClearByTypeOnlyRemovesThatType() {
        NekoEsmVirtualModuleRegistry registry = cache.virtualModules();
        registry.register("server_scripts/a.mjs", "export default 1");
        registry.register("server_scripts/c.mjs#cjs-interop", "export default 4");
        registry.register("client_scripts/b.mjs", "export default 2");
        registry.register("some-package", "export default 3");

        registry.clear(ScriptType.SERVER);

        assertFalse(isVirtual(registry, "server_scripts/a.mjs"), "SERVER 类型虚拟模块必须被清除");
        assertFalse(isVirtual(registry, "server_scripts/c.mjs#cjs-interop"), "带合成后缀的 SERVER 模块必须被清除");
        assertTrue(isVirtual(registry, "client_scripts/b.mjs"), "CLIENT 类型虚拟模块必须保留");
        assertTrue(isVirtual(registry, "some-package"), "跨类型共享模块（裸包名）必须保留");
    }

    @Test
    void esmRegistryClearByTypeClearsItsVirtualPathAndKeepsOtherTypes() {
        NekoEsmVirtualModuleRegistry registry = cache.virtualModules();
        registry.register("server_scripts/x.mjs", "export default 1");
        registry.register("client_scripts/y.mjs", "export default 2");

        Path serverPath = Path.of(registry.uri("server_scripts/x.mjs"));
        Path clientPath = Path.of(registry.uri("client_scripts/y.mjs"));

        registry.clear(ScriptType.SERVER);

        assertNull(registry.source(serverPath), "SERVER 类型 source 必须被清除");
        assertNull(registry.displayPath(serverPath), "SERVER 类型 display path 必须被清除");
        assertNotNull(registry.source(clientPath), "CLIENT 类型 source 必须保留");
        assertNotNull(registry.displayPath(clientPath), "CLIENT 类型 display path 必须保留");
    }

    @Test
    void esmRegistryInvalidateKeepsFileNameIndexConsistent() throws Exception {
        NekoEsmVirtualModuleRegistry registry = cache.virtualModules();
        registry.register("server_scripts/x.mjs", "export default 1");
        registry.register("client_scripts/y.mjs", "export default 2");

        Path serverPath = Path.of(registry.uri("server_scripts/x.mjs"));
        Path clientPath = Path.of(registry.uri("client_scripts/y.mjs"));

        registry.invalidate("server_scripts/x.mjs");

        assertNull(registry.source(serverPath), "invalidate 后 SERVER 类型 source 必须清除");
        assertNull(registry.displayPath(serverPath), "invalidate 后 SERVER 类型 display path 必须清除");
        assertNotNull(registry.source(clientPath), "invalidate SERVER 不能影响 CLIENT 类型 source");
    }

    @Test
    void runtimeRegistryInstancesCannotReadOrClearEachOthersEntries() {
        NekoModulePipelineCache other = new NekoModulePipelineCache(
                new NekoModulePipeline(new NekoCompilationPipeline(), ScriptCompilerRegistry.createRuntimeRegistry(),
                        SandboxConfig.defaultConfig()), new SourceMapRegistry(NekoJSPaths.get().root()),
                new NekoEsmVirtualModuleRegistry(NekoJSPaths.get().root()), NekoTrustContext.local());
        try {
            Path sameVirtualPath = Path.of(cache.virtualModules().register(
                    "server_scripts/shared.mjs", "export const owner = 'first';"));
            Path otherPath = Path.of(other.virtualModules().register(
                    "server_scripts/shared.mjs", "export const owner = 'second';"));
            assertEquals(sameVirtualPath, otherPath);
            assertEquals("export const owner = 'first';", cache.virtualModules().source(sameVirtualPath));
            assertEquals("export const owner = 'second';", other.virtualModules().source(otherPath));

            cache.clear();

            assertNull(cache.virtualModules().source(sameVirtualPath));
            assertEquals("export const owner = 'second';", other.virtualModules().source(otherPath));
        } finally {
            other.clear();
        }
    }

    private static boolean isVirtual(NekoEsmVirtualModuleRegistry registry, String moduleId) {
        return registry.isVirtualModule(Path.of(registry.uri(moduleId)));
    }

    private static void writeModule(Path path) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, "module.exports = 'cache-clear';\n");
    }

    private static String minimalMap(String source) {
        return "{\"version\":3,\"file\":\"generated.js\",\"sourceRoot\":\"\",\"sources\":[\""
                + source + "\"],\"names\":[],\"mappings\":\"AAAA\"}";
    }
}
