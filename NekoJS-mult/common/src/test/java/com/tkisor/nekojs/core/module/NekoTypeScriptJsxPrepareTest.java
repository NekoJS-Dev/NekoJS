package com.tkisor.nekojs.core.module;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tkisor.nekojs.core.compiler.IScriptCompiler;
import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.NekoJsxLanguagePlugin;
import com.tkisor.nekojs.core.compiler.NekoModuleMode;
import com.tkisor.nekojs.core.compiler.NekoTsxLanguagePlugin;
import com.tkisor.nekojs.core.compiler.NekoTypeScriptLanguagePlugin;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.SourceMapRegistry;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 票据 12 AC1 / AC4 / AC6：.ts、.jsx、.tsx 经 {@link NekoModulePipelineCache#prepare} 得到
 * 正确 language id、module mode、可执行 code 与可用 source map；准备期语法/转换错误携带
 * 原始 authored 文件与行列；源码 / 语言 / mode 变化使 cache key 失效。
 *
 * <p>断言口径是最高调用者 seam 的可观察结果（prepared module 字段、异常对象字段、cache key），
 * 不读私有 AST 布局。
 */
class NekoTypeScriptJsxPrepareTest {

    @TempDir
    Path gameDir;

    private NekoJSPaths paths;
    private ScriptCompilerRegistry compilers;
    private NekoModulePipelineCache cache;

    @BeforeEach
    void setUp() throws Exception {
        TestPlatformInit.ensureInitialized(gameDir);
        paths = pathsFor(gameDir);
        Files.createDirectories(paths.serverScripts());
        compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        compilers.register(NekoTypeScriptLanguagePlugin.INSTANCE);
        compilers.register(NekoJsxLanguagePlugin.INSTANCE);
        compilers.register(NekoTsxLanguagePlugin.INSTANCE);
        cache = new NekoModulePipelineCache(
                new NekoModulePipeline(new NekoCompilationPipeline(), compilers, SandboxConfig.defaultConfig()),
                new SourceMapRegistry(paths.root()), new NekoEsmVirtualModuleRegistry(paths.root()),
                NekoTrustContext.local());
    }

    private Path write(String name, String source) throws IOException {
        Path file = paths.serverScripts().resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
        return file;
    }

    // ---- AC1: language id / mode / code / source map ----

    @Test
    void typescriptJsxAndTsxPublishLanguageModeCodeAndUsableMap() throws Exception {
        String tsSource = "interface Named { name: string }\n"
                + "const value: number = 41 + 1;\n"
                + "export const answer: number = value;\n"
                + "export const label: Named = { name: 'ts' };\n";
        NekoPreparedModule ts = cache.prepare(write("prepare/ident.ts", tsSource));
        assertEquals("typescript", ts.languageId());
        assertEquals(NekoModuleMode.ESM, ts.mode());
        assertTrue(ts.code().contains("41 + 1"), ts.code());
        assertFalse(ts.code().contains(": number"), "annotations must be erased: " + ts.code());
        assertUsableMap(ts, tsSource);

        String jsxSource = "const view = <div id=\"x\">{1 + 1}</div>;\nmodule.exports = view;\n";
        NekoPreparedModule jsx = cache.prepare(write("prepare/ident.jsx", jsxSource));
        assertEquals("jsx", jsx.languageId());
        assertEquals(NekoModuleMode.COMMONJS, jsx.mode());
        assertTrue(jsx.code().contains("__nekoJsxFactory('div'"), jsx.code());
        assertFalse(jsx.code().contains("<div"), "JSX must be lowered: " + jsx.code());
        assertUsableMap(jsx, jsxSource);

        String tsxSource = "interface Props { id: string }\n"
                + "const props: Props = { id: 'tsx' };\n"
                + "const view = <div id={props.id}>{2 + 2}</div>;\n"
                + "module.exports = view;\n";
        NekoPreparedModule tsx = cache.prepare(write("prepare/ident.tsx", tsxSource));
        assertEquals("tsx", tsx.languageId(), "tsx must keep its own language identity, not collapse into jsx");
        assertEquals(NekoModuleMode.COMMONJS, tsx.mode());
        assertTrue(tsx.code().contains("__nekoJsxFactory('div'"), tsx.code());
        assertFalse(tsx.code().contains(": Props"), "tsx annotations must be erased: " + tsx.code());
        assertFalse(tsx.code().contains("<div"), "tsx JSX must be lowered: " + tsx.code());
        assertUsableMap(tsx, tsxSource);
    }

    @Test
    void jsxAndTsxStayDistinctLanguageIdentitiesForTheSameExtensionFamily() throws Exception {
        NekoModulePipeline pipeline = new NekoModulePipeline(
                new NekoCompilationPipeline(), compilers, SandboxConfig.defaultConfig());

        assertEquals("jsx", pipeline.identify(paths.serverScripts().resolve("a.jsx")).languageId());
        assertEquals("tsx", pipeline.identify(paths.serverScripts().resolve("a.tsx")).languageId());
        assertEquals(NekoModuleMode.AUTO, pipeline.identify(paths.serverScripts().resolve("a.tsx")).requestedMode());
        assertEquals(NekoModuleMode.ESM, pipeline.identify(paths.serverScripts().resolve("a.mjs")).requestedMode());
        assertEquals(NekoModuleMode.COMMONJS, pipeline.identify(paths.serverScripts().resolve("a.cjs")).requestedMode());
    }

    // ---- AC4: prepare-stage failures carry authored file/line/column ----

    @Test
    void typescriptTransformErrorCarriesAuthoredFileLineAndColumn() throws Exception {
        Path file = write("prepare/decorator.ts", "class Plain { }\n@Component\nclass Decorated { }\n");

        Exception failure = assertThrows(Exception.class, () -> cache.prepare(file));

        NekoModuleError staged = NekoModulePipelinePrepareTest.assertStaged(
                failure, NekoModuleError.Stage.PREPARE, NekoModuleError.OWNER_PREPARATION);
        assertTrue(staged.sourcePath().replace('\\', '/').endsWith("prepare/decorator.ts"), staged.detail());
        assertEquals(2, staged.sourceLine(), staged.detail());
        assertEquals(1, staged.sourceColumn(), staged.detail());
    }

    @Test
    void jsxAndTsxStructureErrorsCarryAuthoredFileLineAndColumn() throws Exception {
        Path jsx = write("prepare/mismatch.jsx",
                "const ok = 1;\nconst bad = <div>\n  </span>;\n");
        NekoModuleError jsxError = NekoModulePipelinePrepareTest.assertStaged(
                assertThrows(Exception.class, () -> cache.prepare(jsx)),
                NekoModuleError.Stage.PREPARE, NekoModuleError.OWNER_PREPARATION);
        assertTrue(jsxError.sourcePath().replace('\\', '/').endsWith("prepare/mismatch.jsx"), jsxError.detail());
        assertEquals(3, jsxError.sourceLine(), jsxError.detail());
        assertEquals(3, jsxError.sourceColumn(), jsxError.detail());

        Path tsx = write("prepare/mismatch.tsx",
                "const ok: number = 1;\nconst bad: unknown = <div>\n  </span>;\n");
        NekoModuleError tsxError = NekoModulePipelinePrepareTest.assertStaged(
                assertThrows(Exception.class, () -> cache.prepare(tsx)),
                NekoModuleError.Stage.PREPARE, NekoModuleError.OWNER_PREPARATION);
        assertTrue(tsxError.sourcePath().replace('\\', '/').endsWith("prepare/mismatch.tsx"), tsxError.detail());
        assertEquals(3, tsxError.sourceLine(), tsxError.detail());
        assertEquals(3, tsxError.sourceColumn(), tsxError.detail());
    }

    // ---- AC6: cache key invalidation and observable hits ----

    @Test
    void unchangedTsJsxTsxInputsHitTheSamePreparedIdentity() throws Exception {
        for (String name : new String[]{"hit.ts", "hit.jsx", "hit.tsx"}) {
            Path file = write("prepare/" + name, hitSource(name));
            NekoPreparedModule first = cache.prepare(file);
            NekoPreparedModule second = cache.prepare(file);
            assertEquals(first.cacheKey(), second.cacheKey(), name + " must be a stable cache hit");
            assertEquals(first.languageId(), second.languageId());
        }
    }

    @Test
    void contentLanguageAndModeChangesInvalidateTsJsxTsxEntries() throws Exception {
        Path tsx = write("prepare/stale.tsx",
                "const view: unknown = <div>{1}</div>;\nmodule.exports = 'AAAA';\n");
        NekoPreparedModule beforeContent = cache.prepare(tsx);
        Files.writeString(tsx, "const view: unknown = <div>{1}</div>;\nmodule.exports = 'BBBB';\n");
        NekoPreparedModule afterContent = cache.prepare(tsx);
        assertTrue(afterContent.code().contains("BBBB"), afterContent.code());
        assertNotEquals(beforeContent.cacheKey(), afterContent.cacheKey(), "tsx content change must invalidate");

        Path ts = write("prepare/stale.ts", "const value: number = 1;\nmodule.exports = value;\n");
        NekoPreparedModule beforeLanguage = cache.prepare(ts);
        assertEquals("typescript", beforeLanguage.languageId());
        compilers.registerLanguage("typescript-replacement", Set.of(".ts"),
                prefixingCompiler(".ts", "// replacement\n"));
        NekoPreparedModule afterLanguage = cache.prepare(ts);
        assertEquals("typescript-replacement", afterLanguage.languageId());
        assertNotEquals(beforeLanguage.cacheKey(), afterLanguage.cacheKey(),
                "ts language identity change must invalidate the prepared entry");

        Path mode = write("prepare/mode.ts", "export const value: number = 1;\n");
        NekoPreparedModule esm = cache.prepare(mode);
        assertEquals(NekoModuleMode.ESM, esm.mode());
        Files.writeString(mode, "module.exports = { value: 1 };\n");
        NekoPreparedModule cjs = cache.prepare(mode);
        assertEquals(NekoModuleMode.COMMONJS, cjs.mode());
        assertNotEquals(esm.cacheKey(), cjs.cacheKey(), "ts mode change must invalidate the prepared entry");

        Path jsx = write("prepare/mode.jsx", "module.exports = <p>jsx</p>;\n");
        NekoPreparedModule beforeJsxLanguage = cache.prepare(jsx);
        assertEquals("jsx", beforeJsxLanguage.languageId());
        compilers.registerLanguage("jsx-replacement", Set.of(".jsx"),
                prefixingCompiler(".jsx", "// jsx-replacement\n"));
        NekoPreparedModule afterJsxLanguage = cache.prepare(jsx);
        assertEquals("jsx-replacement", afterJsxLanguage.languageId());
        assertNotEquals(beforeJsxLanguage.cacheKey(), afterJsxLanguage.cacheKey(),
                "jsx language identity change must invalidate the prepared entry");
    }

    private static IScriptCompiler prefixingCompiler(String extension, String prefix) {
        return new IScriptCompiler() {
            @Override
            public boolean canCompile(String candidate) {
                return extension.equalsIgnoreCase(candidate);
            }

            @Override
            public String compile(Path file, String sourceCode) {
                return prefix + sourceCode;
            }
        };
    }

    private static String hitSource(String name) {
        if (name.endsWith(".tsx")) {
            return "const view: unknown = <div>{1}</div>;\nmodule.exports = view;\n";
        }
        if (name.endsWith(".jsx")) {
            return "const view = <div>{1}</div>;\nmodule.exports = view;\n";
        }
        return "const value: number = 1;\nmodule.exports = value;\n";
    }

    /** Source map must be non-empty and map every generated line onto a real authored line. */
    private void assertUsableMap(NekoPreparedModule prepared, String authoredSource) {
        assertNotNull(prepared.sourceMap(), "every prepared TS/JSX/TSX module must publish a source map");
        JsonObject root = JsonParser.parseString(prepared.sourceMap()).getAsJsonObject();
        assertEquals(3, root.get("version").getAsInt());
        assertFalse(root.get("mappings").getAsString().isBlank(), "source map must carry mappings");

        SourceMapRegistry registry = new SourceMapRegistry(paths.root());
        registry.register(prepared.sourcePath(), prepared.sourceMap());
        int authoredLines = authoredSource.split("\\n", -1).length;
        int generatedLines = prepared.code().split("\\n", -1).length;
        for (int generatedLine = 1; generatedLine <= generatedLines; generatedLine++) {
            SourceMapRegistry.OriginalPosition mapped = registry.getMappedPosition(
                    prepared.sourcePath(), generatedLine, 1);
            assertTrue(mapped.line >= 1 && mapped.line <= authoredLines,
                    "generated line " + generatedLine + " must map into authored lines, was " + mapped);
        }
    }

    private static NekoJSPaths pathsFor(Path gameDir) throws Exception {
        Constructor<NekoJSPaths> constructor = NekoJSPaths.class.getDeclaredConstructor(Path.class);
        constructor.setAccessible(true);
        return constructor.newInstance(gameDir);
    }
}
