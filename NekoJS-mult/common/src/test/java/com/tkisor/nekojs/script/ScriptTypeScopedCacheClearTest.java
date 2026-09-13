package com.tkisor.nekojs.script;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.error.SourceMapRegistry;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.module.NekoModulePreparation;
import com.tkisor.nekojs.core.module.NekoPreparedModule;
import com.tkisor.nekojs.testfixture.TestModulePreparation;
import com.tkisor.nekojs.core.module.esm.NekoEsmVirtualModuleRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 进程级静态缓存按 {@link ScriptType} 分区的回归测试（直接测缓存，不经 Graal Context）。
 *
 * <p>背景缺陷：prepared module cache / SourceMapRegistry / NekoEsmVirtualModuleRegistry 均无
 * ScriptType 维度，单机 CLIENT 触发 reload 会误清 SERVER 等其它类型的编译模块、source map 与
 * 虚拟 ESM URI。修复后新增 {@code clear(ScriptType)} / {@code clearByScriptType(ScriptType)}
 * 按类型局部清除，本测试直接验证独立性，并验证跨类型共享条目（node_modules / 裸包名
 * moduleId）不受影响。
 *
 * <p>工单 11：prepared module cache 不再是 process-wide static{@code NekoModulePipelineCache}，
 * 而是 runtime owner 持有的实例级 {@link NekoModulePreparation}；测试构造独立实例，
 * 不再反射私有 static {@code PREPARED_CACHE}。
 */
class ScriptTypeScopedCacheClearTest {

    @BeforeAll
    static void bindPaths() {
        TestPlatformInit.ensureInitialized();
    }

    @BeforeEach
    @AfterEach
    void clearCaches() throws Exception {
        removeTypeScopedScripts();
        preparation().clear();
        NekoEsmVirtualModuleRegistry.clear();
        SourceMapRegistry.clear();
    }

    // ---- NekoModulePreparation（实例级 prepared cache，工单 11） ----

    @Test
    void pipelineCacheClearByTypeOnlyRemovesThatType() throws Exception {
        NekoJSPaths paths = NekoJSPaths.get();
        NekoModulePreparation preparation = preparation();
        Path serverScript = writeScript(paths.serverScripts(), "helper.cjs", "module.exports = 1;\n");
        Path clientScript = writeScript(paths.clientScripts(), "helper.cjs", "module.exports = 2;\n");
        Path sharedScript = writeScript(paths.nodeModules().resolve("pkg"), "index.cjs", "module.exports = 3;\n");

        preparation.prepare(serverScript);
        preparation.prepare(clientScript);
        preparation.prepare(sharedScript);
        assertTrue(preparation.isCached(serverScript) && preparation.isCached(clientScript) && preparation.isCached(sharedScript),
                "测试前提：三个脚本都必须先进入缓存");

        preparation.clear(ScriptType.SERVER);

        assertFalse(preparation.isCached(serverScript), "SERVER 类型的 prepared 条目必须被清除");
        assertTrue(preparation.isCached(clientScript), "CLIENT 类型的 prepared 条目必须保留");
        assertTrue(preparation.isCached(sharedScript), "跨类型共享缓存（node_modules）必须保留");
    }

    @Test
    void pipelineCacheNoArgClearWipesEverything() throws Exception {
        NekoJSPaths paths = NekoJSPaths.get();
        NekoModulePreparation preparation = preparation();
        Path serverScript = writeScript(paths.serverScripts(), "wipe.cjs", "module.exports = 1;\n");
        Path sharedScript = writeScript(paths.nodeModules().resolve("pkg"), "wipe.cjs", "module.exports = 2;\n");
        preparation.prepare(serverScript);
        preparation.prepare(sharedScript);

        preparation.clear();

        assertTrue(!preparation.isCached(serverScript) && !preparation.isCached(sharedScript),
                "无参 clear 必须清空本实例全部条目");
        assertTrue(preparation.size() == 0, "无参 clear 后缓存必须为空");
    }

    // ---- SourceMapRegistry ----

    @Test
    void sourceMapClearByTypeOnlyRemovesThatType() {
        SourceMapRegistry.register("server_scripts/foo.ts", minimalMap("server_scripts/foo.ts"));
        SourceMapRegistry.register("client_scripts/bar.ts", minimalMap("client_scripts/bar.ts"));

        SourceMapRegistry.clearByScriptType(ScriptType.SERVER);

        assertNull(SourceMapRegistry.getMappedPosition("server_scripts/foo.ts", 1, 0).path,
                "SERVER 类型 source map 必须被清除");
        assertNotNull(SourceMapRegistry.getMappedPosition("client_scripts/bar.ts", 1, 0).path,
                "CLIENT 类型 source map 必须保留");
    }

    // ---- NekoEsmVirtualModuleRegistry ----

    @Test
    void esmRegistryClearByTypeOnlyRemovesThatType() {
        NekoEsmVirtualModuleRegistry.register("server_scripts/a.mjs", "export default 1");
        NekoEsmVirtualModuleRegistry.register("server_scripts/c.mjs#cjs-interop", "export default 4");
        NekoEsmVirtualModuleRegistry.register("client_scripts/b.mjs", "export default 2");
        NekoEsmVirtualModuleRegistry.register("some-package", "export default 3");

        NekoEsmVirtualModuleRegistry.clear(ScriptType.SERVER);

        assertFalse(isVirtual("server_scripts/a.mjs"), "SERVER 类型虚拟模块必须被清除");
        assertFalse(isVirtual("server_scripts/c.mjs#cjs-interop"), "带合成后缀的 SERVER 模块必须被清除");
        assertTrue(isVirtual("client_scripts/b.mjs"), "CLIENT 类型虚拟模块必须保留");
        assertTrue(isVirtual("some-package"), "跨类型共享模块（裸包名）必须保留");
    }

    @Test
    void esmRegistryClearByTypeLeavesNoFileNameIndexOrphans() throws Exception {
        NekoEsmVirtualModuleRegistry.register("server_scripts/x.mjs", "export default 1");
        NekoEsmVirtualModuleRegistry.register("client_scripts/y.mjs", "export default 2");

        Path serverPath = Path.of(NekoEsmVirtualModuleRegistry.uri("server_scripts/x.mjs"));
        Path clientPath = Path.of(NekoEsmVirtualModuleRegistry.uri("client_scripts/y.mjs"));

        NekoEsmVirtualModuleRegistry.clear(ScriptType.SERVER);

        assertNull(NekoEsmVirtualModuleRegistry.source(serverPath), "SERVER 类型 source 必须被清除");
        assertNull(NekoEsmVirtualModuleRegistry.displayPath(serverPath), "SERVER 类型 display path 必须被清除");
        assertNotNull(NekoEsmVirtualModuleRegistry.source(clientPath), "CLIENT 类型 source 必须保留");
        assertNotNull(NekoEsmVirtualModuleRegistry.displayPath(clientPath), "CLIENT 类型 display path 必须保留");

        Map<String, String> displayByFileName = esmRegistryMap("DISPLAY_PATHS_BY_FILE_NAME");
        Map<String, String> keyByFileName = esmRegistryMap("KEY_BY_FILE_NAME");
        assertFalse(displayByFileName.containsValue("server_scripts/x.mjs"),
                "DISPLAY_PATHS_BY_FILE_NAME 不能残留 SERVER 类型的孤儿条目");
        assertFalse(keyByFileName.containsKey(serverPath.getFileName().toString()),
                "KEY_BY_FILE_NAME 不能残留 SERVER 类型的孤儿条目");
        assertTrue(displayByFileName.containsValue("client_scripts/y.mjs"),
                "CLIENT 类型的 file-name 条目必须保留");
        assertTrue(keyByFileName.containsKey(clientPath.getFileName().toString()),
                "CLIENT 类型的 key-by-file-name 条目必须保留");
    }

    @Test
    void esmRegistryInvalidateKeepsFileNameIndexConsistent() throws Exception {
        NekoEsmVirtualModuleRegistry.register("server_scripts/x.mjs", "export default 1");
        NekoEsmVirtualModuleRegistry.register("client_scripts/y.mjs", "export default 2");

        Path serverPath = Path.of(NekoEsmVirtualModuleRegistry.uri("server_scripts/x.mjs"));
        Path clientPath = Path.of(NekoEsmVirtualModuleRegistry.uri("client_scripts/y.mjs"));

        NekoEsmVirtualModuleRegistry.invalidate("server_scripts/x.mjs");

        assertNull(NekoEsmVirtualModuleRegistry.source(serverPath), "invalidate 后 SERVER 类型 source 必须清除");
        assertNull(NekoEsmVirtualModuleRegistry.displayPath(serverPath), "invalidate 后 SERVER 类型 display path 必须清除");
        assertNotNull(NekoEsmVirtualModuleRegistry.source(clientPath), "invalidate SERVER 不能影响 CLIENT 类型 source");

        Map<String, String> displayByFileName = esmRegistryMap("DISPLAY_PATHS_BY_FILE_NAME");
        Map<String, String> keyByFileName = esmRegistryMap("KEY_BY_FILE_NAME");
        assertFalse(displayByFileName.containsKey(serverPath.getFileName().toString()),
                "invalidate 后 DISPLAY_PATHS_BY_FILE_NAME 不能残留 SERVER 条目");
        assertFalse(keyByFileName.containsKey(serverPath.getFileName().toString()),
                "invalidate 后 KEY_BY_FILE_NAME 不能残留 SERVER 条目");
        assertTrue(displayByFileName.containsKey(clientPath.getFileName().toString()),
                "invalidate SERVER 不能影响 CLIENT 的 file-name 条目");
        assertTrue(keyByFileName.containsKey(clientPath.getFileName().toString()),
                "invalidate SERVER 不能影响 CLIENT 的 key-by-file-name 条目");
    }

    private static boolean isVirtual(String moduleId) {
        return NekoEsmVirtualModuleRegistry.isVirtualModule(Path.of(NekoEsmVirtualModuleRegistry.uri(moduleId)));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> esmRegistryMap(String fieldName) throws Exception {
        Field field = NekoEsmVirtualModuleRegistry.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (Map<String, String>) field.get(null);
    }

    // ---- helpers: 实例级 NekoModulePreparation（工单 11，不再反射 static PREPARED_CACHE） ----

    private static NekoModulePreparation preparation() {
        return TestModulePreparation.create(NekoJSPaths.get(), SandboxConfig.defaultConfig());
    }

    private static Path writeScript(Path dir, String name, String source) throws Exception {
        Files.createDirectories(dir);
        Path script = dir.resolve(name);
        Files.writeString(script, source);
        script.toFile().deleteOnExit();
        return script;
    }

    /** 清理本测试写入的类型脚本与 node_modules 探针（固定 gameDir，不能靠 @TempDir）。 */
    private static void removeTypeScopedScripts() throws Exception {
        NekoJSPaths paths = NekoJSPaths.get();
        for (Path dir : List.of(paths.serverScripts(), paths.clientScripts())) {
            deleteIfExists(dir.resolve("helper.cjs"));
            deleteIfExists(dir.resolve("wipe.cjs"));
        }
        deleteIfExists(paths.nodeModules().resolve("pkg/index.cjs"));
        deleteIfExists(paths.nodeModules().resolve("pkg/wipe.cjs"));
    }

    private static void deleteIfExists(Path path) throws Exception {
        Files.deleteIfExists(path);
    }

    private static String minimalMap(String source) {
        return "{\"version\":3,\"file\":\"generated.js\",\"sourceRoot\":\"\",\"sources\":[\""
                + source + "\"],\"names\":[],\"mappings\":\"AAAA\"}";
    }
}
