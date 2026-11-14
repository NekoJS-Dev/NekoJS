package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.api.event.ScriptBindingSchema;
import com.tkisor.nekojs.core.compiler.IScriptCompiler;
import com.tkisor.nekojs.core.compiler.NekoAstLowering;
import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.NekoIRProgram;
import com.tkisor.nekojs.core.compiler.NekoLanguagePlugin;
import com.tkisor.nekojs.core.compiler.NekoLexer;
import com.tkisor.nekojs.core.compiler.NekoModuleMode;
import com.tkisor.nekojs.core.compiler.NekoParser;
import com.tkisor.nekojs.core.compiler.NekoRawSourceLexer;
import com.tkisor.nekojs.core.compiler.NekoTypeScriptLanguagePlugin;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.compiler.python.PythonToJsCompiler;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** JS-only checks use authored native JS or the captured frontend's generated JavaScript. */
class NekoModulePipelinePreflightGateTest {
    @BeforeAll
    static void bindPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @Test
    void overriddenJsFrontendChecksGeneratedCodeInsteadOfItsRawSyntax() throws Exception {
        for (String extension : List.of(".js", ".custom")) {
            for (boolean legacy : List.of(false, true)) {
                for (String output : List.of("const value = 1;", "Fixture.missing();")) {
                    ScriptCompilerRegistry registry = ScriptCompilerRegistry.createRuntimeRegistry();
                    if (legacy) {
                        registry.registerLanguage("javascript", Set.of(extension), new IScriptCompiler() {
                            public boolean canCompile(String ext) { return extension.equals(ext); }
                            public String compile(Path file, String source) { return output; }
                        });
                    } else {
                        registry.register(new NekoLanguagePlugin() {
                            public String id() { return "javascript"; }
                            public Set<String> extensions() { return Set.of(extension); }
                            public NekoLexer lexer() { return NekoRawSourceLexer.INSTANCE; }
                            public NekoParser parser() { return tokens -> tokens::source; }
                            public NekoAstLowering lowering() {
                                return ast -> new NekoIRProgram("javascript", output, null,
                                        NekoModuleMode.COMMONJS, false, false);
                            }
                        });
                    }
                    List<ScriptBindingSchema.Diagnostic> diagnostics = prepare(registry,
                            "frontend-override" + extension, "def declare_machines():\n    pass\n");
                    assertTrue(diagnostics.stream().noneMatch(d -> message(d).contains("declare_machines")),
                            diagnostics.toString());
                    if (output.contains("missing")) {
                        assertTrue(diagnostics.stream().anyMatch(d -> message(d).contains("missing")),
                                "Generated JS typos must still be checked");
                    } else {
                        assertTrue(diagnostics.isEmpty(), diagnostics.toString());
                    }
                }
            }
        }
    }

    @Test
    void nativeJavaScriptStillReportsRealBindingTypos() throws Exception {
        ScriptCompilerRegistry registry = ScriptCompilerRegistry.createRuntimeRegistry();
        assertTrue(prepare(registry, "native.js", "Fixture.valid();").isEmpty());
        assertFalse(prepare(registry, "native.js", "Fixture.missing();").isEmpty());
    }

    @Test
    void pythonSourcePreparesWithoutJsPreflightMisfires() throws Exception {
        ScriptCompilerRegistry registry = ScriptCompilerRegistry.createRuntimeRegistry();
        registry.registerLanguage("python", Set.of(".py"), new PythonToJsCompiler());
        assertTrue(prepare(registry, "python.py", "# comment\ndef greet():\n    return 7\nFixture.valid()\ngreet()\n").isEmpty());
        assertTrue(prepare(registry, "python.py", "Fixture.missing()\n").stream()
                .anyMatch(d -> message(d).contains("missing")));
    }

    @Test
    void typescriptSourcePreparesWithoutJsPreflightMisfires() throws Exception {
        ScriptCompilerRegistry registry = ScriptCompilerRegistry.createRuntimeRegistry();
        registry.register(NekoTypeScriptLanguagePlugin.INSTANCE);
        assertTrue(prepare(registry, "typescript.ts", "interface Block { getName(): string }\n"
                + "const width: number = 3;\nFixture.valid();\n").isEmpty());
        assertTrue(prepare(registry, "typescript.ts", "const width: number = 3;\nFixture.missing();\n")
                .stream().anyMatch(d -> message(d).contains("missing")));
    }

    private static List<ScriptBindingSchema.Diagnostic> prepare(ScriptCompilerRegistry registry,
                                                               String name, String source) throws Exception {
        NekoModulePipeline pipeline = new NekoModulePipeline(
                new NekoCompilationPipeline(), registry, SandboxConfig.defaultConfig());
        Path file = Path.of("server_scripts", name).toAbsolutePath();
        var diagnostics = new ArrayList<ScriptBindingSchema.Diagnostic>();
        var view = new ScriptBindingSchema.View(
                Map.of("Fixture", new ScriptBindingSchema.BindingMembers(Set.of("valid"))),
                Set.of("console"), diagnostics::add);
        pipeline.prepareCaptured(file, source, NekoTrustApprovedSource.local(file), pipeline.captureBinding(file), view);
        return diagnostics;
    }

    private static String message(ScriptBindingSchema.Diagnostic diagnostic) {
        return diagnostic.throwable().getMessage();
    }
}
