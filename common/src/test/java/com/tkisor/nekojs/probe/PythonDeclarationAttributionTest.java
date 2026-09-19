package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.BindingCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.probe.backend.python.PythonProbeBackend;
import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
