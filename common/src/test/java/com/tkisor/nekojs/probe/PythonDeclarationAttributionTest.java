package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.BindingCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.plugin.NekoPluginBootstrap;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.probe.backend.python.PythonProbeBackend;
import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks that Python declaration and probe output keep their own language and module attribution and
 * are not overwritten by JS/TS declarations.
 *
 * <p>The Python and TypeScript backends consume the same {@link NekoScriptCatalogSnapshot} and the
 * same shared IR, and each renders into its own output directory. In one run neither overwrites the
 * other's language id, output directory or module attribution ({@code ProbeCoordinator} rejects a
 * shared output directory, and {@code ProbeBackend#outputDir} is derived from the language id). The
 * test observes the language ids in the builtin registry plus the real output directories and output
 * content of both backends.
 */
class PythonDeclarationAttributionTest {

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    /**
     * The real path this protection has to hold on: the TS and Python backends are both present in one
     * {@link ProbeCoordinator} run, which is what the {@code /nekojs probe all} command does. The test
     * asserts that each backend writes into its own language directory and neither overwrites the
     * other. It is not a synthetic proof built by constructing two backends with {@code new} and
     * generating from them separately.
     */
    @Test
    void coordinatorRunsTypeScriptAndPythonTogetherWithoutOverwritingEachOther(@TempDir Path temp)
            throws Exception {
        NekoJSPaths paths = NekoJSPaths.fromGameDir(temp);
        Files.createDirectories(paths.root());

        // Real registry session: registers both builtin backends through the extension point and locks the
        // registry, the same way production bootstrap does.
        Field inst = ProbeBackendRegistry.class.getDeclaredField("INSTANCE");
        inst.setAccessible(true);
        inst.set(null, null);
        NekoPluginBootstrap.bootstrap(List.of(new NekoProbeBuiltinPlugin()), new ScriptPropertyRegistry.Impl());

        ProbeCoordinator coordinator = new ProbeCoordinator(paths, ProbeExternalArtifacts.NONE);
        List<ProbeBackend> both = List.of(
                ProbeBackendSelector.named("typescript", "builtin").get(0),
                ProbeBackendSelector.named("python", "builtin").get(0));

        TypeDecl fixture = new TypeDecl(TypeDecl.Kind.CLASS, Host.class, Host.class.getName());
        List<ProbeBackend.GenerateResult> results = coordinator.runProbe(
                snapshotWith(List.of(BindingCatalogEntry.of("Host", ScriptType.SERVER, Host.class, false))),
                both);

        assertEquals(2, results.size(), "both selected backends must produce a result");
        Path tsDir = paths.gameDir().resolve(".neko_probe").resolve("typescript");
        Path pyDir = paths.gameDir().resolve(".neko_probe").resolve("python");
        for (ProbeBackend.GenerateResult result : results) {
            assertTrue(result.success(), "backend result must succeed: " + result.message());
        }
        assertEquals(tsDir, results.get(0).outputDir(), "the TS backend owns the typescript directory");
        assertEquals(pyDir, results.get(1).outputDir(), "the Python backend owns the python directory");

        // Neither overwrites the other: both language outputs exist, and neither has the other's shape.
        assertTrue(Files.exists(pyDir.resolve("nekojs/py.typed")), "Python must emit its PEP 561 marker");
        assertTrue(Files.exists(pyDir.resolve("nekojs/__init__.pyi")), "Python must emit its entry stub");
        assertTrue(Files.exists(tsDir.resolve("index.d.ts")) || Files.isDirectory(tsDir),
                "TypeScript must emit into its own directory: " + tsDir);

        try (var pyFiles = Files.walk(pyDir)) {
            List<Path> generated = pyFiles.filter(Files::isRegularFile).toList();
            assertFalse(generated.isEmpty(), "python directory must not be empty");
            assertTrue(generated.stream().noneMatch(p -> p.toString().endsWith(".d.ts")),
                    "TS declarations must not land in the Python language directory: " + generated);
        }
        try (var tsFiles = Files.walk(tsDir)) {
            List<Path> generated = tsFiles.filter(Files::isRegularFile).toList();
            assertTrue(generated.stream().noneMatch(p -> p.toString().endsWith(".pyi")),
                    "Python declarations must not land in the TypeScript language directory: " + generated);
        }
    }

    /**
     * {@link ProbeCoordinator} rejects two backends that share an output directory in one run. That
     * check is what keeps one language's declarations from overwriting the other's.
     */
    @Test
    void coordinatorRejectsTwoBackendsSharingOneOutputDirectory(@TempDir Path temp) throws Exception {
        NekoJSPaths paths = NekoJSPaths.fromGameDir(temp);
        Files.createDirectories(paths.root());

        ProbeCoordinator coordinator = new ProbeCoordinator(paths, ProbeExternalArtifacts.NONE);
        // Point the python output directory at the typescript one, so the two backends share it.
        Path shared = paths.gameDir().resolve(".neko_probe").resolve("shared");
        ProbeBackend ts = new DirectoryPinnedBackend("typescript", "pinned", shared, "ts.txt", "ts");
        ProbeBackend py = new DirectoryPinnedBackend("python", "pinned", shared, "py.txt", "py");

        List<ProbeBackend.GenerateResult> results =
                coordinator.runProbe(snapshotWith(List.of()), List.of(ts, py));

        assertEquals(2, results.size());
        assertTrue(results.get(0).success(), results.get(0).message());
        assertFalse(results.get(1).success(), "the second backend must be skipped, not overwrite the first");
        assertTrue(results.get(1).message().contains("duplicate output directory"), results.get(1).message());
        assertEquals("ts", Files.readString(shared.resolve("ts.txt")),
                "the first backend's artifact must survive the duplicate-directory run");
        assertFalse(Files.exists(shared.resolve("py.txt")),
                "the skipped backend must not have written into the shared directory");
    }

    /**
     * A probe backend with a fixed output directory, used to exercise the coordinator's duplicate
     * directory check.
     */
    private static final class DirectoryPinnedBackend implements ProbeBackend {
        private final String languageId;
        private final String name;
        private final Path outputDir;
        private final String fileName;
        private final String content;

        DirectoryPinnedBackend(String languageId, String name, Path outputDir, String fileName, String content) {
            this.languageId = languageId;
            this.name = name;
            this.outputDir = outputDir;
            this.fileName = fileName;
            this.content = content;
        }

        @Override
        public String languageId() {
            return languageId;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Path outputDir(NekoJSPaths paths, ProbeConfig config) {
            return outputDir;
        }

        @Override
        public Map<String, String> render(ProbeContext ctx) {
            return Map.of(fileName, content);
        }
    }

    @Test
    void pythonBackendKeepsItsOwnLanguageIdAndOutputDirectory() {
        PythonProbeBackend python = new PythonProbeBackend();
        com.tkisor.nekojs.probe.backend.typescript.TypeScriptProbeBackend typescript =
                new com.tkisor.nekojs.probe.backend.typescript.TypeScriptProbeBackend();

        assertEquals("python", python.languageId(), "Python probe must keep its own language id");
        assertEquals("builtin", python.name());
        assertEquals("typescript", typescript.languageId(), "the TS backend must not claim the Python id");

        NekoJSPaths paths = NekoJSPaths.fromGameDir(Path.of(".").toAbsolutePath());
        assertEquals(paths.gameDir().resolve(".neko_probe").resolve("python"),
                python.outputDir(paths, ProbeConfig.defaultConfig()),
                "Python output must land in its own language directory");
        assertEquals(paths.gameDir().resolve(".neko_probe").resolve("typescript"),
                typescript.outputDir(paths, ProbeConfig.defaultConfig()),
                "TS output must land in its own language directory, not overwrite Python's");
    }

    @Test
    void pythonDeclarationIsDerivedFromTheSharedCatalogAndKeepsModuleAttribution(@TempDir Path temp)
            throws Exception {
        // The same runtime member input: both the TS and the Python declaration derive from it, but on the
        // Python side the module is the Java package path.
        TypeDecl fixture = new TypeDecl(TypeDecl.Kind.CLASS, Host.class, Host.class.getName());
        NekoScriptCatalogSnapshot snapshot = snapshotWith(
                List.of(BindingCatalogEntry.of("Host", ScriptType.SERVER, Host.class, false)));

        Path out = temp.resolve(".neko_probe").resolve("python");
        ProbeContext ctx = new ProbeContext.Of(snapshot, List.of(), ProbeConfig.defaultConfig(),
                NekoJSPaths.fromGameDir(temp), "python", out, List.of(fixture), null);
        ProbeBackend.GenerateResult result = new PythonProbeBackend().generate(ctx);

        assertTrue(result.success(), "python probe generate failed: " + result.message());
        assertEquals(out, result.outputDir(), "the result must report the Python language directory");

        String modulePath = "nekojs/_java/com/tkisor/nekojs/probe/__init__.pyi";
        assertTrue(Files.exists(out.resolve(modulePath)),
                "the Python module must be attributed to the Java package path: " + modulePath);
        String module = Files.readString(out.resolve(modulePath));
        assertTrue(module.contains("class PythonDeclarationAttributionTest_Host"),
                "the runtime member must appear under its own Python module: " + module);

        // Language attribution: the output is a .pyi with the PEP 561 marker, not a TS .d.ts.
        assertTrue(Files.exists(out.resolve("nekojs/py.typed")), "PEP 561 marker must be emitted");
        assertTrue(Files.exists(out.resolve("nekojs/__init__.pyi")),
                "the Python global-binding entry must be emitted");
        try (var files = Files.walk(out)) {
            List<Path> generated = files.filter(Files::isRegularFile).toList();
            assertTrue(generated.stream().allMatch(p -> !p.toString().endsWith(".d.ts")),
                    "the Python backend must not emit TypeScript declarations: " + generated);
        }
    }

    @Test
    void pythonDeclarationDoesNotClaimJsLanguageIdentity(@TempDir Path temp) throws Exception {
        TypeDecl fixture = new TypeDecl(TypeDecl.Kind.CLASS, Host.class, Host.class.getName());
        Path out = temp.resolve(".neko_probe").resolve("python");
        ProbeContext ctx = new ProbeContext.Of(snapshotWith(List.of()), List.of(), ProbeConfig.defaultConfig(),
                NekoJSPaths.fromGameDir(temp), "python", out, List.of(fixture), null);

        assertTrue(new PythonProbeBackend().generate(ctx).success());
        String init = Files.readString(out.resolve("nekojs/__init__.pyi"));
        assertFalse(init.contains("declare const"), "Python declarations must not use TS syntax: " + init);
        assertTrue(init.contains("__all__"), "Python entry must export __all__: " + init);
    }

    /** Runtime member fixture with a stable FQN. */
    public static final class Host {
        public String getLabel() {
            return "label";
        }
    }

    private static NekoScriptCatalogSnapshot snapshotWith(List<BindingCatalogEntry> bindings) {
        return new NekoScriptCatalogSnapshot(
                List.of(), bindings, List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, new LinkedHashMap<>(), List.of());
    }
}