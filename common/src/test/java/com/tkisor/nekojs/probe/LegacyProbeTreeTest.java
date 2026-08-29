package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.probe.backend.typescript.TypeScriptProbeBackend;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Generates the full probe tree from {@link LegacyProbeFixture#snapshot()} using
 * {@link TypeScriptProbeBackend}（Phase 1 起替代旧 ProbeOrchestrator）, then compares
 * every file against golden resources under {@code /nekojs/probe/legacy-tree/}.
 *
 * <p><b>保留决策</b>：内容比对与 {@link ProbeOutputCompatibilityTest} 重叠，但本测试额外校验
 * 「无多余文件」（golden 之外仅允许 {@code @nekojs/managed/} 子树），能捕获意外新增的包目录/
 * 产物文件——因此保留而非删除。归一化已从「全行排序 + import 排序」收紧为与
 * {@link ProbeOutputCompatibilityTest} 相同的字节级口径（仅 CRLF + 行尾空白归一）：
 * 排序会掩盖成员顺序回归，而产物顺序本身已有确定性保证（TypeReflector 排序 + 字典序 import）。
 */
class LegacyProbeTreeTest {

    private static final String GOLDEN_BASE_PATH = "/nekojs/probe/legacy-tree/";

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @TempDir
    Path tempDir;

    @Test
    void legacyTreeMatchesGolden() throws Exception {
        NekoScriptCatalogSnapshot snapshot = LegacyProbeFixture.snapshot();
        var result = generateAt(snapshot, tempDir.resolve("probe-types"));
        assertTrue(result.success(), "Probe generation should succeed: " + result.message());

        Map<String, String> actualFiles = readTree(tempDir.resolve("probe-types"));

        // 重生成模式（-Dnekojs.golden.regenerate=true，由 gradle 任务 regenerateGoldens 设置）：
        // 实际产物镜像覆盖 golden 资源目录后跳过断言（跑完人工 review + 提交）
        if (ProbeGoldenSupport.regenerateEnabled()) {
            Path goldenDir = ProbeGoldenSupport.resourceDir(getClass(), GOLDEN_BASE_PATH);
            assertNotNull(goldenDir, "golden tree resources must resolve to a file: URL under " + GOLDEN_BASE_PATH);
            ProbeGoldenSupport.mirrorTree(tempDir.resolve("probe-types"), goldenDir);
            Assumptions.assumeTrue(false, "goldens regenerated; review and commit");
        }

        Map<String, String> expectedFiles = readGoldenTree();

        // Verify golden tree exists
        assertFalse(expectedFiles.isEmpty(), "Golden tree resources must exist under " + GOLDEN_BASE_PATH);

        // Check all expected files exist with matching content
        for (var entry : expectedFiles.entrySet()) {
            String relPath = entry.getKey();
            String expectedContent = entry.getValue();
            assertTrue(actualFiles.containsKey(relPath), "Missing generated file: " + relPath);
            assertEquals(expectedContent, actualFiles.get(relPath),
                    "Content mismatch for " + relPath);
        }

        // Check no extra files generated (allowing managed declarations subtree)
        for (String relPath : actualFiles.keySet()) {
            if (relPath.startsWith("@nekojs/managed/")) {
                continue;
            }
            assertTrue(expectedFiles.containsKey(relPath), "Unexpected generated file: " + relPath);
        }
    }

    /**
     * 用 TypeScriptProbeBackend 生成到指定目录；显式传入旧 5-前缀白名单以与 golden tree 字节可比。
     */
    private ProbeBackend.GenerateResult generateAt(NekoScriptCatalogSnapshot snapshot, Path outputDir) {
        ProbeConfig cfg = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of("java", "net.minecraft", "net.minecraftforge", "net.neoforged", "com.tkisor.nekojs"),
                List.of(), List.of(), List.of("minecraft"), 5, "SMART"));
        List<Class<?>> collected = new ArrayList<>(ProbeCoordinator.collectClasses(snapshot, cfg));
        TypeScriptProbeBackend backend = new TypeScriptProbeBackend();
        ProbeContext ctx = new ProbeContext.Of(snapshot, collected, cfg, NekoJSPaths.get(), "typescript", outputDir);
        return backend.generate(ctx);
    }

    private Map<String, String> readTree(Path root) throws IOException {
        Map<String, String> files = new TreeMap<>();
        if (!Files.exists(root)) return files;
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String rel = root.relativize(file).toString().replace('\\', '/');
                files.put(rel, normalize(Files.readString(file, StandardCharsets.UTF_8)));
                return FileVisitResult.CONTINUE;
            }
        });
        return files;
    }

    private Map<String, String> readGoldenTree() throws IOException {
        Map<String, String> files = new TreeMap<>();
        Path dir = ProbeGoldenSupport.resourceDir(LegacyProbeTreeTest.class, GOLDEN_BASE_PATH);
        if (dir == null) return files;

        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String rel = dir.relativize(file).toString().replace('\\', '/');
                files.put(rel, normalize(Files.readString(file, StandardCharsets.UTF_8)));
                return FileVisitResult.CONTINUE;
            }
        });
        return files;
    }

    /** 与 {@link ProbeOutputCompatibilityTest} 同一口径：仅 CRLF + 行尾空白归一，不做任何重排。 */
    private static String normalize(String value) {
        return value.replace("\r\n", "\n").stripTrailing();
    }
}
