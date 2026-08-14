package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.api.surface.*;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that Probe output includes legacy files alongside managed declarations,
 * and that repeated generation produces identical output.
 */
class ProbeOutputCompatibilityTest {

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @TempDir
    Path tempDir;

    @Test
    void legacyFilesStillExistWithManagedOutput() throws Exception {
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

        // All legacy golden files must still exist
        Map<String, String> legacyGolden = readGoldenTree();
        assertFalse(legacyGolden.isEmpty(), "Legacy golden tree must exist");

        for (String relPath : legacyGolden.keySet()) {
            assertTrue(actualFiles.containsKey(relPath),
                    "Legacy file missing from generated output: " + relPath);
        }

        // Phase 2.7（IR 唯一渲染路径）回归护栏：legacy 文件内容必须与录制树逐字一致，
        // 单次反射多产物（TypeReflector → 声明 + import 集合）不得改变任何已有产物字节
        for (Map.Entry<String, String> entry : legacyGolden.entrySet()) {
            String actual = actualFiles.get(entry.getKey());
            if (!entry.getValue().equals(actual)) {
                throw new AssertionError("Legacy golden content drift for " + entry.getKey()
                        + System.lineSeparator() + goldenDiff(entry.getKey(), entry.getValue(), actual));
            }
        }
    }

    /** 诊断：golden 与实际内容的逐行差异摘要（临时排查用，定位跨平台漂移）。 */
    private static String goldenDiff(String file, String golden, String actual) {
        List<String> g = golden == null ? List.of() : java.util.Arrays.asList(golden.split("\n", -1));
        List<String> a = actual == null ? List.of() : java.util.Arrays.asList(actual.split("\n", -1));
        StringBuilder sb = new StringBuilder();
        sb.append("golden lines=").append(g.size()).append(" actual lines=").append(a.size());
        int shown = 0;
        for (int i = 0; i < Math.max(g.size(), a.size()) && shown < 12; i++) {
            String gs = i < g.size() ? g.get(i) : "<EOF>";
            String as = i < a.size() ? a.get(i) : "<EOF>";
            if (gs.equals(as)) continue;
            sb.append(System.lineSeparator()).append("L").append(i + 1).append(" G: ")
              .append(gs.length() > 120 ? gs.substring(0, 120) + "…" : gs);
            sb.append(System.lineSeparator()).append("L").append(i + 1).append(" A: ")
              .append(as.length() > 120 ? as.substring(0, 120) + "…" : as);
            shown++;
        }
        return sb.toString();
    }

    @Test
    void repeatedGenerationProducesIdenticalFileHashes() throws Exception {
        NekoScriptCatalogSnapshot snapshot = LegacyProbeFixture.snapshot();

        Path probeDir1 = tempDir.resolve("run1").resolve("probe-types");
        Path probeDir2 = tempDir.resolve("run2").resolve("probe-types");

        var result1 = generateAt(snapshot, probeDir1);
        assertTrue(result1.success(), result1.message());
        var result2 = generateAt(snapshot, probeDir2);
        assertTrue(result2.success(), result2.message());

        Map<String, String> files1 = readTree(probeDir1);
        Map<String, String> files2 = readTree(probeDir2);

        // Same set of files
        assertEquals(files1.keySet(), files2.keySet(),
                "File sets should be identical across runs");

        // Same content for each file
        for (String relPath : files1.keySet()) {
            assertEquals(files1.get(relPath), files2.get(relPath),
                    "Content mismatch for " + relPath + " across runs");
        }
    }

    /**
     * 用 TypeScriptProbeBackend（Phase 1 起替代旧 ProbeOrchestrator）生成到指定目录。
     * 显式传入旧 5-前缀白名单，确保与 golden tree 字节可比（无视测试平台的 defaultScanPackages）。
     */
    private ProbeGenerator.GenerateResult generateAt(NekoScriptCatalogSnapshot snapshot, Path outputDir) {
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

    private static final String GOLDEN_BASE_PATH = "/nekojs/probe/legacy-tree/";

    private Map<String, String> readGoldenTree() throws IOException {
        Map<String, String> files = new TreeMap<>();
        Path dir = ProbeGoldenSupport.resourceDir(ProbeOutputCompatibilityTest.class, GOLDEN_BASE_PATH);
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

    private static String normalize(String value) {
        return value.replace("\r\n", "\n").stripTrailing();
    }
}
