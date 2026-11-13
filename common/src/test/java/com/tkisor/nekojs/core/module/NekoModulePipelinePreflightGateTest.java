package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.NekoTypeScriptLanguagePlugin;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.compiler.python.PythonToJsCompiler;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for the preflight language gate: the JS-only ValParser preflight only runs on
 * extensions whose original source is JS. The original source of .py (with # comments, def/class)
 * and .ts (with type annotations, interface) would be split into bogus calls and produce
 * systematic "Unknown identifier" false positives in the error panel. For transpiled languages the
 * preflight runs on the compiled output instead.
 */
class NekoModulePipelinePreflightGateTest {

    @BeforeAll
    static void bindPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @Test
    void rawPreflightAppliesOnlyToJsFamilyExtensions() {
        for (String js : new String[]{".js", ".mjs", ".cjs", ".jsx"}) {
            assertTrue(NekoModulePipeline.rawPreflightApplies(js), js + " raw source is JS");
        }
        for (String transpiled : new String[]{".py", ".ts", ".tsx", ".mts", ".cts", ".pyw", ""}) {
            assertFalse(NekoModulePipeline.rawPreflightApplies(transpiled),
                    transpiled + " raw source must not run the JS-only preflight");
        }
    }

    private static NekoModulePipeline pipelineWithLanguages() {
        // The runtime registers these through the @RegisterNekoJSPlugin annotation; this bare test
        // registers python and typescript by hand.
        ScriptCompilerRegistry registry = ScriptCompilerRegistry.createRuntimeRegistry();
        registry.registerLanguage("python", Set.of(".py"), new PythonToJsCompiler());
        registry.register(NekoTypeScriptLanguagePlugin.INSTANCE);
        return new NekoModulePipeline(new NekoCompilationPipeline(), registry, SandboxConfig.defaultConfig());
    }

    /** Original .py source with # comments and def prepares successfully: transpilation works and the
    preflight runs on the output, not on the original source. */
    @Test
    void pythonSourcePreparesWithoutJsPreflightMisfires() throws Exception {
        NekoModulePipeline pipeline = pipelineWithLanguages();
        NekoPreparedModule module = pipeline.prepare(Path.of("preflight-gate-test.py"),
                "# comment line\n"
                + "def greet(name):\n"
                + "    return 'hi ' + name\n"
                + "value = greet('world')\n");
        assertNotNull(module);
        assertNotNull(module.code());
        assertFalse(module.code().contains("def greet"), "python must be transpiled, not passed through");
    }

    /** Original .ts source with type annotations and an interface declaration prepares successfully and
    the output is valid JS. */
    @Test
    void typescriptSourcePreparesWithoutJsPreflightMisfires() throws Exception {
        NekoModulePipeline pipeline = pipelineWithLanguages();
        NekoPreparedModule module = pipeline.prepare(Path.of("preflight-gate-test.ts"),
                "interface Block { getName(): string }\n"
                + "const width: number = 3;\n"
                + "const out = width * 2;\n");
        assertNotNull(module);
        assertFalse(module.code().contains(": number"), "type annotations must be erased: " + module.code());
    }
}
