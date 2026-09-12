package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.probe.backend.python.PythonProbeBackend;
import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC2/AC5（ticket 09）Python 侧确定性派生 fixture：同一 runtime member 输入
 * （{@link TypeReflector} 反射同一组 fixture 类）重复生成 Python declaration（.pyi）
 * 两次，整棵产物树逐字节一致；且 .pyi 成员与 runtime member parity（成员名、
 * module 归属）。产物只写入 {@code @TempDir}，不触碰任何 golden。
 */
class PythonDeclarationDeterminismParityTest {

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    /** fixture：public 实例方法（runtime member）。 */
    public static final class ParityFixture {
        public String getLabel() { return "label"; }
        public int compute(int base, int delta) { return base + delta; }
        public boolean isReady() { return true; }
    }

    /** fixture：静态方法 + 字段访问器，覆盖静态段与 getter 语义。 */
    public static final class StaticFixture {
        public static String of(String id) { return id; }
        public String getName() { return "static"; }
    }

    @TempDir
    Path tempA;

    @TempDir
    Path tempB;

    @Test
    void pythonDeclarationIsByteStableAcrossRepeatedGeneration() throws Exception {
        Map<String, String> first = generateTree();
        Map<String, String> second = generateTree();

        assertEquals(first.keySet(), second.keySet(),
                "repeated Python declaration generation must produce the same file set");
        for (String relPath : first.keySet()) {
            assertEquals(first.get(relPath), second.get(relPath),
                    "byte drift in repeated Python declaration generation: " + relPath);
        }
    }

    @Test
    void pythonDeclarationMembersHaveRuntimeParity() throws Exception {
        Map<String, String> tree = generateTree();

        String module = tree.get("nekojs/_java/com/tkisor/nekojs/probe/__init__.pyi");
        assertTrue(module != null, "fixture package module missing; files: " + tree.keySet());

        // 类归属：fixture 类型必须出现在其运行时包对应的 module 里（module attribution；
        // 嵌套类名按渲染规则把 $ 展平为 _）
        assertTrue(module.contains("class PythonDeclarationDeterminismParityTest_ParityFixture"),
                "ParityFixture missing: " + module);
        assertTrue(module.contains("class PythonDeclarationDeterminismParityTest_StaticFixture"),
                "StaticFixture missing: " + module);

        // runtime member parity（Python 渲染语义）：
        // - 普通/静态方法 → def（静态为 @staticmethod）
        // - getXxx/isXxx getter → @property 属性（名去 get/is 前缀）
        assertMemberPresent(module, "def compute(self, base: int, delta: int) -> int");
        assertMemberPresent(module, "@property\n    def label(self) -> str");
        assertMemberPresent(module, "@property\n    def ready(self) -> bool");
        assertMemberPresent(module, "@staticmethod\n    def of(id: str) -> str");
        assertMemberPresent(module, "@property\n    def name(self) -> str");

        // 产物不得声明 runtime 上不存在的方法（以一个不存在的名字为负样本）
        assertTrue(!module.contains("def notARuntimeMember("), "phantom member leaked into declaration");
    }

    // ---------- helpers ----------

    private static void assertMemberPresent(String module, String memberDeclaration) {
        assertTrue(module.contains(memberDeclaration),
                "runtime member missing from Python declaration: " + memberDeclaration + "\n" + module);
    }

    /** 反射 fixture → IR → PythonProbeBackend.generate，返回相对路径 → 文件内容的整棵树。 */
    private Map<String, String> generateTree() throws IOException {
        List<TypeDecl> ir = List.of(
                new TypeReflector().reflect(ParityFixture.class),
                new TypeReflector().reflect(StaticFixture.class));

        NekoScriptCatalogSnapshot snapshot = emptySnapshot();
        ProbeConfig cfg = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of("com.tkisor.nekojs.probe"), List.of(), List.of(), List.of(), 5, "SMART"));
        NekoJSPaths paths = NekoJSPaths.fromGameDir(tempA);
        Files.createDirectories(paths.root());
        Path outputDir = tempB.resolve("probe-python");
        ProbeContext ctx = new ProbeContext.Of(snapshot, List.of(), cfg, paths, "python", outputDir, ir);

        ProbeBackend.GenerateResult result = new PythonProbeBackend().generate(ctx);
        assertTrue(result.success(), "python probe generate failed: " + result.message());

        Map<String, String> tree = new TreeMap<>();
        try (Stream<Path> files = Files.walk(outputDir)) {
            List<Path> sorted = new ArrayList<>(files.filter(Files::isRegularFile).sorted().toList());
            for (Path file : sorted) {
                tree.put(outputDir.relativize(file).toString().replace('\\', '/'),
                        Files.readString(file));
            }
        }
        return tree;
    }

    private static NekoScriptCatalogSnapshot emptySnapshot() {
        return new NekoScriptCatalogSnapshot(
                ScriptType.all(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                new com.tkisor.nekojs.api.catalog.TypeOutputLayout(Path.of("probe-types"), Path.of("snippets")),
                Map.of(), List.of());
    }
}
