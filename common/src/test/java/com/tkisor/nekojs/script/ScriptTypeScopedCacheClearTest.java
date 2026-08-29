package com.tkisor.nekojs.script;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.error.SourceMapRegistry;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.module.NekoModulePipelineCache;
import com.tkisor.nekojs.core.module.NekoPreparedModule;
import com.tkisor.nekojs.core.module.esm.NekoEsmVirtualModuleRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 进程级静态缓存按 {@link ScriptType} 分区的回归测试（直接测缓存，不经 Graal Context）。
 *
 * <p>背景缺陷：NekoModulePipelineCache / SourceMapRegistry / NekoEsmVirtualModuleRegistry 均为
 * 进程级静态缓存且无 ScriptType 维度，单机 CLIENT 触发 reload 会误清 SERVER 等其它类型的
 * 编译模块、source map 与虚拟 ESM URI。修复后新增 {@code clear(ScriptType)} /
 * {@code clearByScriptType(ScriptType)} 按类型局部清除，本测试直接验证独立性，
 * 并验证跨类型共享条目（node_modules / 裸包名 moduleId）不受影响。
 */
class ScriptTypeScopedCacheClearTest {

    @BeforeAll
    static void bindPaths() {
        TestPlatformInit.ensureInitialized();
    }

    @BeforeEach
    @AfterEach
    void clearCaches() {
        NekoModulePipelineCache.clear();
        NekoEsmVirtualModuleRegistry.clear();
        SourceMapRegistry.clear();
    }

    // ---- NekoModulePipelineCache ----

    @Test
    void pipelineCacheClearByTypeOnlyRemovesThatType() throws Exception {
        Path root = NekoJSPaths.get().root().toAbsolutePath().normalize();
        Path serverKey = root.resolve("server_scripts/helper.js");
        Path clientKey = root.resolve("client_scripts/helper.js");
        Path sharedKey = root.resolve("node_modules/pkg/index.js");

        Map<Path, Object> cache = pipelineCache();
        Object stamp = newStamp(1L, 10L, "hash");
        NekoPreparedModule prepared = NekoPreparedModule.commonJs("export default 1", null);
        cache.put(serverKey, newEntry(stamp, prepared, ScriptType.SERVER));
        cache.put(clientKey, newEntry(stamp, prepared, ScriptType.CLIENT));
        cache.put(sharedKey, newEntry(stamp, prepared, null));

        NekoModulePipelineCache.clear(ScriptType.SERVER);

        assertFalse(cache.containsKey(serverKey), "SERVER 类型的 prepared 条目必须被清除");
        assertTrue(cache.containsKey(clientKey), "CLIENT 类型的 prepared 条目必须保留");
        assertTrue(cache.containsKey(sharedKey), "跨类型共享缓存（node_modules）必须保留");
    }

    @Test
    void pipelineCacheNoArgClearWipesEverything() throws Exception {
        Path root = NekoJSPaths.get().root().toAbsolutePath().normalize();
        Map<Path, Object> cache = pipelineCache();
        Object stamp = newStamp(1L, 10L, "hash");
        NekoPreparedModule prepared = NekoPreparedModule.commonJs("export default 1", null);
        cache.put(root.resolve("server_scripts/a.js"), newEntry(stamp, prepared, ScriptType.SERVER));
        cache.put(root.resolve("node_modules/pkg/index.js"), newEntry(stamp, prepared, null));

        NekoModulePipelineCache.clear();

        assertTrue(cache.isEmpty(), "无参 clear 必须清空全部条目");
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

    // ---- reflection helpers for the private static PREPARED_CACHE ----

    @SuppressWarnings("unchecked")
    private static Map<Path, Object> pipelineCache() throws Exception {
        Field field = NekoModulePipelineCache.class.getDeclaredField("PREPARED_CACHE");
        field.setAccessible(true);
        return (Map<Path, Object>) field.get(null);
    }

    private static Object newStamp(long millis, long size, String contentHash) throws Exception {
        Class<?> stampClass = Class.forName("com.tkisor.nekojs.core.module.NekoModulePipelineCache$FileStamp");
        Constructor<?> ctor = stampClass.getDeclaredConstructor(long.class, long.class, String.class);
        ctor.setAccessible(true);
        return ctor.newInstance(millis, size, contentHash);
    }

    private static Object newEntry(Object stamp, NekoPreparedModule prepared, ScriptType type) throws Exception {
        Class<?> entryClass = Class.forName("com.tkisor.nekojs.core.module.NekoModulePipelineCache$PreparedEntry");
        Constructor<?> ctor = entryClass.getDeclaredConstructor(
                Class.forName("com.tkisor.nekojs.core.module.NekoModulePipelineCache$FileStamp"),
                NekoPreparedModule.class,
                ScriptType.class);
        ctor.setAccessible(true);
        return ctor.newInstance(stamp, prepared, type);
    }

    private static String minimalMap(String source) {
        return "{\"version\":3,\"file\":\"generated.js\",\"sourceRoot\":\"\",\"sources\":[\""
                + source + "\"],\"names\":[],\"mappings\":\"AAAA\"}";
    }
}
