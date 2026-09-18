package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.IScriptCompiler;
import com.tkisor.nekojs.core.compiler.NekoModuleMode;
import com.tkisor.nekojs.core.compiler.NekoTypeScriptLanguagePlugin;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.SourceMapRegistry;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 票据 11 AC1 + AC2：{@link NekoModulePipeline#prepare} 最高调用者测试。
 *
 * <p>JS/CJS/ESM 输入产生正确的 language id、module mode、可执行 code、可解析的 identity
 * source map 与稳定 cache key；prepared module 不可变（record + final
 * 组件 + 篡改即 key 变化）。
 */
class NekoModulePipelinePrepareTest {

    @BeforeAll
    static void bindPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    private static NekoModulePipeline pipeline() {
        ScriptCompilerRegistry registry = ScriptCompilerRegistry.createRuntimeRegistry();
        return new NekoModulePipeline(new NekoCompilationPipeline(), registry, SandboxConfig.defaultConfig());
    }

    @Test
    void jsPreparesAsCommonJsWithLanguageIdentityAndStableKey() throws Exception {
        NekoModulePipeline pipeline = pipeline();
        String source = "module.exports = 40 + 2;\n";

        NekoPreparedModule first = pipeline.prepare(Path.of("server_scripts/prepare-a.js"), source);
        NekoPreparedModule second = pipeline.prepare(Path.of("server_scripts/prepare-a.js"), source);

        assertEquals("javascript", first.languageId());
        assertEquals(NekoModuleMode.COMMONJS, first.mode());
        assertTrue(first.code().contains("40 + 2"), "code must be executable JS, was: " + first.code());
        assertIdentityMap(first, source);
        assertNotNull(first.sourcePath());
        assertEquals(first.cacheKey(), second.cacheKey(), "same input must produce a stable cache key");
    }

    @Test
    void mjsForcesEsmModeWithDistinctKeyFromCjs() throws Exception {
        NekoModulePipeline pipeline = pipeline();
        String source = "export const value = 7;\n";

        NekoPreparedModule esm = pipeline.prepare(Path.of("server_scripts/prepare-b.mjs"), source);
        NekoPreparedModule cjs = pipeline.prepare(Path.of("server_scripts/prepare-b.cjs"), "module.exports = 7;\n");

        assertEquals("javascript", esm.languageId());
        assertEquals(NekoModuleMode.ESM, esm.mode());
        assertNotNull(esm.esmAst(), "ESM prepared module must carry its AST for the linker");
        assertIdentityMap(esm, source);
        assertEquals(NekoModuleMode.COMMONJS, cjs.mode());
        assertIdentityMap(cjs, "module.exports = 7;\n");
        assertNotEquals(esm.cacheKey(), cjs.cacheKey(), "mode/content change must change the cache key");
    }

    @Test
    void compilerBackedTypescriptPublishesItsActualSourceMap() throws Exception {
        ScriptCompilerRegistry registry = ScriptCompilerRegistry.createRuntimeRegistry();
        registry.register(NekoTypeScriptLanguagePlugin.INSTANCE);
        NekoModulePipeline pipeline = new NekoModulePipeline(
                new NekoCompilationPipeline(), registry, SandboxConfig.defaultConfig());

        NekoPreparedModule prepared = pipeline.prepare(
                Path.of("server_scripts/prepare-map.ts"), "const value: number = 7;\nmodule.exports = value;\n");

        assertEquals("typescript", prepared.languageId());
        assertNotNull(prepared.sourceMap(), "compiler-backed TypeScript must publish its actual map");
        assertTrue(prepared.sourceMap().contains("sourcesContent"), prepared.sourceMap());
    }

    @Test
    void nativeAndLegacyFallbackMapsAlwaysResolveToExistingAuthoredLines() throws Exception {
        NekoModulePipeline pipeline = pipeline();
        String nativeSource = "const first = 1;\nconst second = 2;\n";
        for (String extension : List.of(".js", ".mjs", ".cjs")) {
            String source = extension.equals(".mjs")
                    ? "export const first = 1;\nexport const second = 2;\n"
                    : nativeSource;
            NekoPreparedModule prepared = pipeline.prepare(Path.of("server_scripts/native" + extension), source);
            assertUsableMap(prepared, source, 2);
        }

        ScriptCompilerRegistry registry = ScriptCompilerRegistry.createRuntimeRegistry();
        registry.registerLanguage("legacy-map", Set.of(".legacy"), new IScriptCompiler() {
            @Override
            public boolean canCompile(String extension) {
                return ".legacy".equalsIgnoreCase(extension);
            }

            @Override
            public String compile(Path file, String sourceCode) {
                return "module.exports = 1;\nmodule.exports = 2;\nmodule.exports = 3;\n";
            }
        });
        NekoPreparedModule legacy = new NekoModulePipeline(
                new NekoCompilationPipeline(), registry, SandboxConfig.defaultConfig()).prepare(
                Path.of("server_scripts/legacy.legacy"), "authored first\nauthored second\n");
        assertUsableMap(legacy, "authored first\nauthored second\n", 2);
    }

    @Test
    void preparationRejectsCompilerRegistryReplacementAfterIdentityCapture() {
        ScriptCompilerRegistry registry = ScriptCompilerRegistry.createRuntimeRegistry();
        AtomicBoolean compileStarted = new AtomicBoolean();
        registry.registerLanguage("mutable", Set.of(".mutable"), new IScriptCompiler() {
            @Override
            public boolean canCompile(String extension) {
                return ".mutable".equalsIgnoreCase(extension);
            }

            @Override
            public String compile(Path file, String sourceCode) {
                if (compileStarted.compareAndSet(false, true)) {
                    registry.replaceLanguage("mutable", Set.of(".mutable"), new IScriptCompiler() {
                        @Override
                        public boolean canCompile(String extension) {
                            return ".mutable".equalsIgnoreCase(extension);
                        }

                        @Override
                        public String compile(Path replacementFile, String replacementSource) {
                            return "module.exports = 'replacement';\n";
                        }
                    });
                }
                return "module.exports = 'captured';\n";
            }
        });
        NekoModulePipeline pipeline = new NekoModulePipeline(
                new NekoCompilationPipeline(), registry, SandboxConfig.defaultConfig());

        Exception failure = assertThrows(Exception.class,
                () -> pipeline.prepare(Path.of("server_scripts/mutable.mutable"), "source"));

        assertStaged(failure, NekoModuleError.Stage.PREPARE, NekoModuleError.OWNER_PREPARATION);
    }

    @Test
    void identifyExposesSourceLanguageAndModeWithoutCompiling() {
        NekoModulePipeline pipeline = pipeline();

        NekoModuleIdentity js = pipeline.identify(Path.of("x/main.js"));
        NekoModuleIdentity mjs = pipeline.identify(Path.of("x/main.mjs"));
        NekoModuleIdentity cjs = pipeline.identify(Path.of("x/main.cjs"));

        assertEquals("javascript", js.languageId());
        assertEquals(NekoModuleMode.AUTO, js.requestedMode());
        assertEquals(NekoModuleMode.ESM, mjs.requestedMode());
        assertEquals(NekoModuleMode.COMMONJS, cjs.requestedMode());
    }

    @Test
    void preparedModuleIsImmutableRecord() throws Exception {
        assertTrue(NekoPreparedModule.class.isRecord(), "NekoPreparedModule must stay a record");
        for (Field field : NekoPreparedModule.class.getDeclaredFields()) {
            if (field.isSynthetic()) {
                continue;
            }
            assertTrue(Modifier.isFinal(field.getModifiers()), "component must be final: " + field.getName());
        }
        NekoPreparedModule module = NekoPreparedModule.commonJs("const a = 1;", null);
        // 依赖表不可变：Resolution/Cache 无法通过取到的 record 改写依赖图输入。
        assertThrows(UnsupportedOperationException.class,
                () -> module.cjsRecord().staticDependencies().add("evil"));
        // 反射篡改即变红：final 组件不接受反射写入。
        Field codeField = NekoPreparedModule.class.getDeclaredField("code");
        codeField.setAccessible(true);
        assertThrows(Exception.class, () -> codeField.set(module, "tampered"),
                "反射改写 record 组件必须失败");
    }

    @Test
    void tamperingWithCodeChangesCacheKey() {
        NekoPreparedModule original = NekoPreparedModule.commonJs("javascript", "server_scripts/t.js",
                "module.exports = 1;", null, com.tkisor.nekojs.core.module.cjs.CjsModuleRecord.EMPTY);
        // “修改”只能构造新实例；新实例 key 必然不同——旧 key 无法指向被篡改内容。
        NekoPreparedModule tampered = new NekoPreparedModule(original.languageId(), original.sourcePath(),
                "module.exports = 2;", original.sourceMap(), original.mode(), original.esmAst(),
                original.cjsRecord(), original.prependedLineCount(), null);

        assertNotEquals(original.cacheKey(), tampered.cacheKey(),
                "Resolution/Cache tampering with code must change the cache key");
        assertEquals(original.cacheKey(),
                NekoPreparedModule.stableCacheKey(original.sourcePath(), original.languageId(), original.mode(),
                        original.code(), original.sourceMap()),
                "cache key must equal the documented stable computation");
    }

    @Test
    void stableCacheKeyCoversPathLanguageModeCodeAndMap() {
        String keyA = NekoPreparedModule.stableCacheKey("server_scripts/a.js", "javascript", NekoModuleMode.COMMONJS, "code", "map");
        assertEquals(keyA, NekoPreparedModule.stableCacheKey("server_scripts/a.js", "javascript", NekoModuleMode.COMMONJS, "code", "map"));
        assertNotEquals(keyA, NekoPreparedModule.stableCacheKey("server_scripts/b.js", "javascript", NekoModuleMode.COMMONJS, "code", "map"));
        assertNotEquals(keyA, NekoPreparedModule.stableCacheKey("server_scripts/a.js", "typescript", NekoModuleMode.COMMONJS, "code", "map"));
        assertNotEquals(keyA, NekoPreparedModule.stableCacheKey("server_scripts/a.js", "javascript", NekoModuleMode.ESM, "code", "map"));
        assertNotEquals(keyA, NekoPreparedModule.stableCacheKey("server_scripts/a.js", "javascript", NekoModuleMode.COMMONJS, "code!", "map"));
        assertNotEquals(keyA, NekoPreparedModule.stableCacheKey("server_scripts/a.js", "javascript", NekoModuleMode.COMMONJS, "code", "map!"));
    }

    private static void assertIdentityMap(NekoPreparedModule prepared, String authoredSource) {
        assertNotNull(prepared.sourceMap(), "every successful prepared module must publish a source map");
        var root = JsonParser.parseString(prepared.sourceMap()).getAsJsonObject();
        assertEquals(3, root.get("version").getAsInt());
        assertEquals(prepared.sourcePath(), root.getAsJsonArray("sources").get(0).getAsString());
        assertEquals(authoredSource, root.getAsJsonArray("sourcesContent").get(0).getAsString());
        assertTrue(!root.get("mappings").getAsString().isBlank(), "identity map must contain mappings");

        SourceMapRegistry registry = new SourceMapRegistry(NekoJSPaths.get().root());
        registry.register(prepared.sourcePath(), prepared.sourceMap());
        SourceMapRegistry.OriginalPosition mapped = registry.getMappedPosition(prepared.sourcePath(), 1, 0);
        assertEquals(prepared.sourcePath().replace('\\', '/'), mapped.path);
        assertEquals(1, mapped.line);
        assertEquals(authoredSource, mapped.sourceContent);
    }

    private static void assertUsableMap(NekoPreparedModule prepared, String authoredSource, int authoredLineCount) {
        assertNotNull(prepared.sourceMap(), "fallback source map must be non-null");
        var root = JsonParser.parseString(prepared.sourceMap()).getAsJsonObject();
        assertEquals(authoredSource, root.getAsJsonArray("sourcesContent").get(0).getAsString());
        SourceMapRegistry registry = new SourceMapRegistry(NekoJSPaths.get().root());
        registry.register(prepared.sourcePath(), prepared.sourceMap());
        int generatedLineCount = prepared.code().split("\\n", -1).length;
        for (int generatedLine = 1; generatedLine <= generatedLineCount; generatedLine++) {
            SourceMapRegistry.OriginalPosition mapped = registry.getMappedPosition(
                    prepared.sourcePath(), generatedLine, 1);
            assertEquals(prepared.sourcePath().replace('\\', '/'), mapped.path,
                    "generated line must retain authored path: " + generatedLine);
            assertTrue(mapped.line >= 1 && mapped.line <= authoredLineCount,
                    "generated line must map to an authored line: " + mapped);
            assertTrue(mapped.column >= 1, "mapped column must be legal: " + mapped);
        }
    }

    @Test
    void unknownExtensionFailsAtPrepareStageWithLocation() {
        NekoModulePipeline pipeline = pipeline();

        Exception failure = assertThrows(Exception.class, () ->
                pipeline.prepare(Path.of("server_scripts/prepare-a.zzz9"), "???"));

        NekoModuleError staged = assertStaged(failure, NekoModuleError.Stage.PREPARE,
                NekoModuleError.OWNER_PREPARATION);
        assertTrue(staged.sourcePath().contains("prepare-a.zzz9"), staged.detail());
    }

    static NekoModuleError assertStaged(Throwable failure, NekoModuleError.Stage stage, String owner) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof NekoModuleError staged
                    && staged.stage() == stage && staged.owner().equals(owner)) {
                return staged;
            }
        }
        throw new AssertionError("Expected NekoModuleError[" + stage + "/" + owner + "] in chain of " + failure
                + ": " + List.of(stackLines(failure)));
    }

    private static String[] stackLines(Throwable failure) {
        java.io.StringWriter writer = new java.io.StringWriter();
        failure.printStackTrace(new java.io.PrintWriter(writer));
        return writer.toString().split("\n");
    }
}
