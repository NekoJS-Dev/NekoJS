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
 * W3 预检语言门禁回归：JS-only 的 ValParser 预检只允许对原始源即 JS 的扩展名跑——
 * .py（# 注释、def/class）与 .ts（类型注解、interface）的原始源会被碎成伪调用，
 * 产生 "Unknown identifier" 系统性误报进错误面板。转译语言的预检改在编译产物上跑。
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
        // 运行时由 @RegisterNekoJSPlugin 自动注册；裸测试手动注册 python/typescript
        ScriptCompilerRegistry registry = ScriptCompilerRegistry.createRuntimeRegistry();
        registry.registerLanguage("python", Set.of(".py"), new PythonToJsCompiler());
        registry.register(NekoTypeScriptLanguagePlugin.INSTANCE);
        return new NekoModulePipeline(new NekoCompilationPipeline(), registry, SandboxConfig.defaultConfig());
    }

    /** .py 原始源（含 # 注释与 def）经管线准备成功：转译正常，预检走产物而非原始源。*/
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

    /** .ts 原始源（类型注解 + interface 声明）经管线准备成功且产物是合法 JS 形态。*/
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
