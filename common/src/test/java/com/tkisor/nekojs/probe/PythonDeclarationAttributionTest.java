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
 * 票据 13 AC7：Python declaration/probe 的语言与 module 归属不被 JS/TS declaration 覆盖。
 *
 * <p>Python 与 TypeScript backend 消费**同一份** {@link NekoScriptCatalogSnapshot} + 同一份
 * 共享 IR，各自渲染到自己的输出目录；同一次运行里两者互不覆盖语言 id、输出目录或 module
 * 归属（{@code ProbeCoordinator} 拒绝共享输出目录，{@code ProbeBackend#outputDir} 按 languageId
 * 派生）。观测点：builtin registry 的 language id、两个 backend 的实际输出目录与产物内容。
 */
class PythonDeclarationAttributionTest {

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    /**
     * AC7 的真实运行路径：**同一次 {@link ProbeCoordinator} 运行**里 TS 与 Python 两个 backend
     * 同时在场（这正是命令层 {@code /nekojs probe all} 的形态）。断言两者各自落在自己的语言
     * 目录、互不覆盖——不是两个 backend 各自被 `new` 出来分别 generate 的构造性证明。
     */
    @Test
    void coordinatorRunsTypeScriptAndPythonTogetherWithoutOverwritingEachOther(@TempDir Path temp)
            throws Exception {
        NekoJSPaths paths = NekoJSPaths.fromGameDir(temp);
        Files.createDirectories(paths.root());

        // 真实 registry 会话：经扩展点注册两个 builtin backend 并 lock（同生产 bootstrap）。
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

        // 互不覆盖：各自的语言产物都在，且都不是对方的形态。
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
     * AC7 保护：{@link ProbeCoordinator} 拒绝同一次运行中两个 backend 共享输出目录——
     * 这条保护正是「语言产物不被另一个语言的 declaration 覆盖」的机制保证。
     */
    @Test
    void coordinatorRejectsTwoBackendsSharingOneOutputDirectory(@TempDir Path temp) throws Exception {
        NekoJSPaths paths = NekoJSPaths.fromGameDir(temp);
        Files.createDirectories(paths.root());

        ProbeCoordinator coordinator = new ProbeCoordinator(paths, ProbeExternalArtifacts.NONE);
        // 把 python 的输出目录配置成 typescript 的目录 → 两者共享。
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

    /** 固定输出目录的探针 backend（用于行使 coordinator 的目录去重保护）。 */
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
        // 同一个 runtime member 输入：TS 与 Python 的声明都由它派生，但 Python 侧模块归属是 Java 包路径。
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

        // 语言归属：产物是 .pyi 且带 PEP 561 marker，不是 TS 的 .d.ts。
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

    /** 稳定 FQN 的 runtime member fixture。 */
    public static final class Host {
        public String getLabel() {
            return "label";
        }
    }

    private static NekoScriptCatalogSnapshot snapshotWith(List<BindingCatalogEntry> bindings) {
        return new NekoScriptCatalogSnapshot(
                List.of(), bindings, List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), null, new LinkedHashMap<>(), List.of());
    }
}