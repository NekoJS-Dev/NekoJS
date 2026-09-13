package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.testfixture.TestModulePreparation;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工单 11 AC10：Preparation 与 Resolution/Cache <strong>不创建 Graal Context、不决定
 * HostAccess、不读取 Minecraft/loader</strong>；Context / HostAccess / bindings / 执行关闭
 * 继续由 Script Execution Environment 负责。
 *
 * <p>证据分两层：
 * <ol>
 *   <li><strong>结构证据</strong>：对 Preparation 与 Resolution/Cache 的源码做令牌级扫描，
 *       断言零 {@code Context.newBuilder} / {@code HostAccess}（真实代码，排除 javadoc 里的
 *       说明性文字）与零 {@code net.minecraft}/{@code net.neoforged}/{@code net.fabricmc} import；</li>
 *   <li><strong>行为证据</strong>：完全不构造任何 Graal Context，纯 Java 跑通
 *       {@link NekoModulePipeline#prepare} 的 JS/CJS/ESM 路径与 {@link NekoModulePreparation}
 *       的缓存命中/失效——若 Preparation 私自创建 Context，本测试会在无引擎环境下失败。</li>
 * </ol>
 */
class NekoModulePreparationBoundaryTest {

    /** Preparation 与 Resolution/Cache 的物理落点（相对仓库根）。 */
    private static final List<String> BOUNDARY_SOURCES = List.of(
            "common/src/main/java/com/tkisor/nekojs/core/module/NekoModulePipeline.java",
            "common/src/main/java/com/tkisor/nekojs/core/module/NekoModulePreparation.java",
            "common/src/main/java/com/tkisor/nekojs/core/module/NekoPreparedModule.java",
            "common/src/main/java/com/tkisor/nekojs/core/module/NekoModuleCacheKey.java",
            "common/src/main/java/com/tkisor/nekojs/core/module/NekoModuleIdentity.java",
            "common/src/main/java/com/tkisor/nekojs/core/module/NekoModuleDiagnosticOrigin.java",
            "common/src/main/java/com/tkisor/nekojs/core/module/NekoModuleResolver.java",
            "common/src/main/java/com/tkisor/nekojs/core/module/NekoResolvedModule.java",
            "common/src/main/java/com/tkisor/nekojs/core/module/NekoModuleDependencyGraph.java",
            "common/src/main/java/com/tkisor/nekojs/core/module/ModuleReloadCoordinator.java",
            "common/src/main/java/com/tkisor/nekojs/core/module/NekoContentHash.java",
            "common/src/main/java/com/tkisor/nekojs/core/module/esm/NekoEsmLinker.java",
            "common/src/main/java/com/tkisor/nekojs/core/module/esm/NekoEsmLinkCache.java",
            "common/src/main/java/com/tkisor/nekojs/core/module/esm/NekoEsmModuleRecord.java",
            "common/src/main/java/com/tkisor/nekojs/core/module/esm/NekoEsmModuleRecordCache.java",
            "common/src/main/java/com/tkisor/nekojs/core/module/esm/NekoNativeEsmSourceRewriter.java",
            "common/src/main/java/com/tkisor/nekojs/core/module/esm/NekoEsmVirtualModuleRegistry.java",
            "common/src/main/java/com/tkisor/nekojs/core/module/cjs/CjsStaticAnalyzer.java",
            "common/src/main/java/com/tkisor/nekojs/core/module/cjs/CjsModuleRecord.java"
    );

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    private static Path repoRoot() {
        Path current = Path.of("").toAbsolutePath();
        // Gradle 测试的工作目录包含 common/build 时向上找到含 common/ 的仓库根
        while (current != null && !Files.isDirectory(current.resolve("common/src/main/java"))) {
            current = current.getParent();
        }
        assertNotNull(current, "must locate repository root from " + Path.of("").toAbsolutePath());
        return current;
    }

    @Test
    void preparationAndResolutionSourcesCreateNoContextOrHostAccess() throws IOException {
        List<String> violations = new ArrayList<>();
        for (String relative : BOUNDARY_SOURCES) {
            Path file = repoRoot().resolve(relative);
            assertTrue(Files.exists(file), "boundary source must exist: " + relative);
            for (String line : Files.readAllLines(file)) {
                String trimmed = line.trim();
                if (trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")) {
                    continue; // javadoc 里的说明性文字不算实现
                }
                if (trimmed.contains("Context.newBuilder")) {
                    violations.add(relative + ": " + trimmed);
                }
                if (trimmed.contains("HostAccess")) {
                    violations.add(relative + ": " + trimmed);
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "Preparation/Resolution 不得创建 Graal Context 或决定 HostAccess，违规：" + violations);
    }

    @Test
    void preparationAndResolutionSourcesReadNoMinecraftOrLoader() throws IOException {
        List<String> violations = new ArrayList<>();
        for (String relative : BOUNDARY_SOURCES) {
            Path file = repoRoot().resolve(relative);
            for (String line : Files.readAllLines(file)) {
                String trimmed = line.trim();
                if (trimmed.startsWith("//") || trimmed.startsWith("*")) {
                    continue;
                }
                if (trimmed.contains("net.minecraft") || trimmed.contains("net.neoforged")
                        || trimmed.contains("net.fabricmc")) {
                    violations.add(relative + ": " + trimmed);
                }
            }
        }
        assertTrue(violations.isEmpty(), "Preparation/Resolution 不得读取 Minecraft/loader：" + violations);
    }

    /**
     * 行为证据：不构造任何 Graal Context，纯 Java 跑通 prepare 的 JS/CJS/ESM 路径与缓存语义。
     * Preparation 若私自创建 Context，本测试会在无引擎/无 host access 环境下失败。
     */
    @Test
    void preparationRunsWithoutAnyGraalContext() throws Exception {
        NekoJSPaths paths = NekoJSPaths.get();
        NekoModulePreparation preparation = TestModulePreparation.create();

        Path cjs = write("boundary-plain.cjs", "module.exports = 1;\n");
        Path esm = write("boundary-esm.mjs", "export const v = 1;\n");
        Path js = write("boundary-auto.js", "module.exports = 2;\n");

        assertEquals(com.tkisor.nekojs.core.compiler.NekoModuleMode.COMMONJS, preparation.prepare(cjs).mode());
        assertEquals(com.tkisor.nekojs.core.compiler.NekoModuleMode.ESM, preparation.prepare(esm).mode());
        assertEquals("javascript", preparation.prepare(js).languageId());

        // 缓存命中/失效语义在不含 Context 的纯 Java 环境同样成立
        assertTrue(preparation.isCached(cjs), "prepared 条目必须入本实例缓存");
        NekoPreparedModule first = preparation.prepare(cjs);
        assertTrue(first == preparation.prepare(cjs), "未变化时必须命中同一实例缓存");
        Files.writeString(cjs, "module.exports = 3;\n");
        NekoPreparedModule afterChange = preparation.prepare(cjs);
        assertTrue(afterChange != first, "内容变化必须产生新的 prepared module");
        assertTrue(afterChange.code().contains("3"), "内容变化必须重编译");
    }

    private final List<Path> written = new ArrayList<>();

    @org.junit.jupiter.api.AfterEach
    void cleanUpWrittenFiles() throws Exception {
        // 真删文件 + 空的父目录：deleteOnExit 只在 JVM 退出时生效，会污染共享 gameDir
        TestModulePreparation.deleteFilesAndEmptyParents(written);
        written.clear();
    }

    private Path write(String name, String source) throws IOException {
        Path file = NekoJSPaths.get().testScripts().resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
        written.add(file);
        return file;
    }
}
