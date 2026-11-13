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
 * Deterministic derivation fixture for the Python side: it reflects the same set of fixture classes
 * ({@link TypeReflector}) into runtime members and generates the Python declarations (.pyi) twice. The
 * whole output tree must be byte-identical between the two runs, and the .pyi members must match the
 * runtime members by name and module attribution. Output only goes to {@code @TempDir}; no golden file
 * is touched.
 */
class PythonDeclarationDeterminismParityTest {

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    /** Fixture: public instance methods (runtime members). */
    public static final class ParityFixture {
        public String getLabel() { return "label"; }
        public int compute(int base, int delta) { return base + delta; }
        public boolean isReady() { return true; }
    }

    /** Fixture: static methods plus field accessors, covering the static section and getter semantics. */
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

        // Class attribution: the fixture types must appear in the module of their runtime package. Nested
        // class names are flattened from $ to _ by the render rules.
        assertTrue(module.contains("class PythonDeclarationDeterminismParityTest_ParityFixture"),
                "ParityFixture missing: " + module);
        assertTrue(module.contains("class PythonDeclarationDeterminismParityTest_StaticFixture"),
                "StaticFixture missing: " + module);

        // Runtime member parity (Python render semantics):
        // - plain and static methods become def (static ones get @staticmethod)
        // - getXxx/isXxx getters become @property attributes, with the get/is prefix dropped from the name
        assertMemberPresent(module, "def compute(self, base: int, delta: int) -> int");
        assertMemberPresent(module, "@property\n    def label(self) -> str");
        assertMemberPresent(module, "@property\n    def ready(self) -> bool");
        assertMemberPresent(module, "@staticmethod\n    def of(id: str) -> str");
        assertMemberPresent(module, "@property\n    def name(self) -> str");

        // The output must not declare methods that do not exist at runtime; a nonexistent name is the negative
        // sample.
        assertTrue(!module.contains("def notARuntimeMember("), "phantom member leaked into declaration");
    }

    // ---------- helpers ----------

    private static void assertMemberPresent(String module, String memberDeclaration) {
        assertTrue(module.contains(memberDeclaration),
                "runtime member missing from Python declaration: " + memberDeclaration + "\n" + module);
    }

    /**
     * Runs reflect fixture -> IR -> {@code PythonProbeBackend.generate} and returns the whole tree as
     * relative path -> file content.
     */
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
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                new com.tkisor.nekojs.api.catalog.TypeOutputLayout(Path.of("probe-types"), Path.of("snippets")),
                Map.of(), List.of());
    }
}
