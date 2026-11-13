package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.ScriptFilePolicy;
import com.tkisor.nekojs.core.compiler.IScriptCompiler;
import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.NekoModuleMode;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.DefaultErrorTracker;
import com.tkisor.nekojs.core.error.SourceMapRegistry;
import com.tkisor.nekojs.core.fs.NekoJSFileSystem;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.fs.SandboxPolicy;
import com.tkisor.nekojs.core.node.NekoNodeModuleInstaller;
import com.tkisor.nekojs.core.module.NekoEsmVirtualModuleRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Source;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.io.IOAccess;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Describes the legacy CJS bridge: its current behavior, the reasons for keeping it and what must
 * hold before it can be removed.
 *
 * <p>The legacy CJS bridge is the compatibility input path where an external language registered
 * through {@link IScriptCompiler} goes through {@code compileDetailed → CJS static analysis}, the
 * legacy branch of {@link NekoModulePipeline}. It also covers the direct install path for node
 * builtin resources, where {@link NekoNodeModuleInstaller} evaluates a manifest without going
 * through the pipeline. This test characterizes both. The reasons for keeping them and the
 * removal conditions are listed below; removal happens as soon as the conditions hold and is not
 * postponed to a final release cleanup, and no public behavior gets a second long-lived pipeline.
 *
 * <p>Why they are kept:
 * <ul>
 *   <li>External language plugin compatibility: registering a language by file suffix and taking
 *       part in the pipeline is a public extension point, so removing the path removes public
 *       language participation;</li>
 *   <li>node builtin resources (classpath manifest) behave differently from user modules: they
 *       have no file identity, cache or reload, so the direct install is its own behavior rather
 *       than a second user pipeline.</li>
 * </ul>
 *
 * <p>Removal conditions, all of which must hold:
 * <ol>
 *   <li>Replacement behavior: the builtin language plugin produces byte-identical code and mode
 *       for the same suffix (corpus comparison);</li>
 *   <li>Declaration: the managed declaration surface covers the module capabilities of that
 *       suffix;</li>
 *   <li>Trace: no {@code IScriptCompiler} registration for that suffix remains in the repository
 *       (grep evidence);</li>
 *   <li>No callers: the legacy branch has no production or test callers (coverage evidence).</li>
 * </ol>
 */
class LegacyCjsBridgeCharacterizationTest {

    @BeforeAll
    static void bindPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    /** An external language through the legacy compiler: the output uses CJS semantics and analysis
    runs on the compiled output. */
    @Test
    void legacyCompilerOutputGoesThroughCjsSemantics() throws Exception {
        ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        compilers.registerLanguage("legacy-upper", Set.of(".upperjs"), new IScriptCompiler() {
            @Override
            public boolean canCompile(String extension) {
                return ".upperjs".equalsIgnoreCase(extension);
            }

            @Override
            public String compile(Path file, String sourceCode) {
                // Simulated transpilation: emits require/module.exports, which the original source does not contain.
                return "const other = require('./other.upperjs');\n"
                        + "module.exports = { lang: 'upper', dep: other };\n";
            }
        });
        // With ESM orchestration off, legacy suffixes take the direct CJS branch (compileDetailed → CJS analysis).
        SandboxConfig cjsOnly = new SandboxConfig(false, false, false, false, false, true, false,
                false);
        NekoModulePipeline pipeline = new NekoModulePipeline(
                new NekoCompilationPipeline(), compilers, cjsOnly);

        NekoPreparedModule prepared = pipeline.prepare(
                Path.of("server_scripts/legacy-a.upperjs"), "UPPER SOURCE");

        assertEquals("legacy-upper", prepared.languageId());
        assertEquals(NekoModuleMode.COMMONJS, prepared.mode());
        assertTrue(prepared.code().contains("module.exports"), "产物必须是编译器输出: " + prepared.code());
        assertTrue(prepared.cjsRecord().staticDependencies().contains("./other.upperjs"),
                "CJS 静态分析必须跑在编译产物上: " + prepared.cjsRecord());
        assertNotNull(prepared.sourceMap(), "legacy compiler without a map must receive an identity source map");
        assertTrue(prepared.sourceMap().contains("UPPER SOURCE"), "identity map must retain authored source content");
        assertNotNull(prepared.cacheKey());
    }

    /** The legacy path produces the same prepared shape: there is no second pipeline. */    @Test
    void legacyPathSharesPreparedShapeWithNativePath() throws Exception {
        ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        compilers.registerLanguage("legacy-idem", Set.of(".idemjs"), new IScriptCompiler() {
            @Override
            public boolean canCompile(String extension) {
                return ".idemjs".equalsIgnoreCase(extension);
            }

            @Override
            public String compile(Path file, String sourceCode) {
                return sourceCode;
            }
        });
        SandboxConfig cjsOnly = new SandboxConfig(false, false, false, false, false, true, false,
                false);
        NekoModulePipeline pipeline = new NekoModulePipeline(
                new NekoCompilationPipeline(), compilers, cjsOnly);

        NekoPreparedModule legacy = pipeline.prepare(Path.of("x/a.idemjs"), "module.exports = 1;\n");
        NekoPreparedModule nativeJs = pipeline.prepare(Path.of("x/a.cjs"), "module.exports = 1;\n");

        assertEquals(nativeJs.mode(), legacy.mode(), "同为 CJS mode");
        assertEquals(nativeJs.code(), legacy.code(), "同源经 legacy 与原生路径产物一致");
        assertNotNull(legacy.cacheKey());
        assertNotNull(nativeJs.cacheKey());
    }

    /** Direct install path for node builtins: it skips the pipeline because classpath resources have no
    file identity or cache. */
    @Test
    void nodeBuiltinsInstallWithoutPipeline() {
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            NekoJSPaths paths = NekoJSPaths.get();
            SandboxConfig config = SandboxConfig.defaultConfig();
            ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
            NekoModulePipelineCache cache = new NekoModulePipelineCache(
                    new NekoModulePipeline(new NekoCompilationPipeline(), compilers, config),
                    new SourceMapRegistry(paths.root()), new NekoEsmVirtualModuleRegistry(paths.root()),
                    NekoTrustContext.local());
            NekoNodeModuleInstaller.install(context, ScriptType.TEST,
                    new NekoModuleResolver(paths.gameDir(), paths.root(), paths.nodeModules(),
                            new ScriptFilePolicy(compilers)),
                    paths, new DefaultErrorTracker(paths, config), config, cache);

            var path = context.eval("js",
                    "globalThis.__nekoNodeResolve('node:path').posix.join('/a', 'b')");
            assertEquals("/a/b", path.asString(), "node 内建直装路径必须可用");
        }
    }

    /** Legacy suffixes load and run through the same resolution and cache path, so require identity
    matches CJS. */
    @Test
    void legacyModuleLoadsThroughSharedResolutionCache(@TempDir Path gameDir) throws Exception {
        TestPlatformInit.ensureInitialized(gameDir);
        NekoJSPaths paths = pathsFor(gameDir);
        Path dir = paths.serverScripts().resolve("src");
        Files.createDirectories(dir);

        ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        compilers.registerLanguage("legacy-upper-load", Set.of(".upperjs"), new IScriptCompiler() {
            @Override
            public boolean canCompile(String extension) {
                return ".upperjs".equalsIgnoreCase(extension);
            }

            @Override
            public String compile(Path file, String sourceCode) {
                return sourceCode;
            }
        });
        NekoModulePipelineCache cache = new NekoModulePipelineCache(
                new NekoModulePipeline(new NekoCompilationPipeline(), compilers, SandboxConfig.defaultConfig()),
                new SourceMapRegistry(paths.root()), new NekoEsmVirtualModuleRegistry(paths.root()),
                NekoTrustContext.local());
        IOAccess ioAccess = IOAccess.newBuilder()
                .fileSystem(new NekoJSFileSystem(paths.root(),
                        new SandboxPolicy(SandboxConfig.defaultConfig(), paths), paths, cache))
                .build();
        try (Context context = Context.newBuilder("js").allowAllAccess(true).allowIO(ioAccess).build()) {
            NekoScriptModuleLoaderHost host = new NekoScriptModuleLoaderHost(
                    context, new NekoModuleResolver(paths.gameDir(), paths.root(), paths.nodeModules(),
                            new ScriptFilePolicy(compilers)), cache);
            context.getBindings("js").putMember("__nekoScriptModuleLoaderHost", host);
            try (var in = getClass().getResourceAsStream("/nekojs/node/internal/script-loader.js")) {
                assertNotNull(in, "script-loader.js must be on the test classpath");
                String loader = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                context.eval(Source.newBuilder("js", loader, "nekojs/node/internal/script-loader.js").build());
            }

            Files.writeString(dir.resolve("legacy-lib.upperjs"), "module.exports = { tag: 'upper' };\n");
            Files.writeString(dir.resolve("legacy-entry.upperjs"),
                    "const lib = require('./legacy-lib.upperjs');\n"
                            + "const again = require('./legacy-lib.upperjs');\n"
                            + "module.exports = { tag: lib.tag, same: lib === again };\n");

            Object loaded = host.loadEntry("./server_scripts/src/legacy-entry.upperjs");
            assertTrue(loaded instanceof Value, "host 应返回 guest Value, was: " + loaded);
            Value exports = (Value) loaded;
            assertEquals("upper", exports.getMember("tag").asString());
            assertTrue(exports.getMember("same").asBoolean(), "legacy 模块 require 身份与 CJS 一致");
        }
    }

    private static NekoJSPaths pathsFor(Path gameDir) throws Exception {
        Constructor<NekoJSPaths> constructor = NekoJSPaths.class.getDeclaredConstructor(Path.class);
        constructor.setAccessible(true);
        return constructor.newInstance(gameDir);
    }

    /** Removal gate, structural evidence: the pipeline holds no static mutable state, and the
    LEGACY_INSTANCE and SHARED facades are gone. */
    @Test
    void pipelineHoldsNoStaticMutableState() {
        for (Field field : NekoModulePipeline.class.getDeclaredFields()) {
            assertTrue(!Modifier.isStatic(field.getModifiers()),
                    "NekoModulePipeline 不得有 static 字段: " + field.getName());
        }
        for (Method method : NekoModulePipeline.class.getDeclaredMethods()) {
            String name = method.getName();
            assertTrue(!name.startsWith("legacy") && !name.startsWith("bindLegacy"),
                    "legacy static 门面必须已删除: " + name);
        }
    }

    /** Removal gate, structural evidence: the prepared cache has no process-wide static state; the
    owner holds the instance. */
    @Test
    void preparationCacheHoldsNoStaticState() {
        for (Field field : NekoModulePipelineCache.class.getDeclaredFields()) {
            if (field.isSynthetic()) {
                continue;
            }
            assertTrue(!Modifier.isStatic(field.getModifiers()),
                    "NekoModulePipelineCache 不得有 static 可变状态: " + field.getName());
        }
    }

    /** Adds no public parser ModuleSPI: public pipeline signatures only expose the pipeline, the
    registry, the config and prepared types. */
    @Test
    void pipelinePublicSurfaceExposesNoParserSpi() {
        Set<String> forbiddenFragments = Set.of(
                "NekoLexer", "NekoParser", "NekoAstLowering", "NekoTokenStream", "NekoSourceAst");
        for (Constructor<?> ctor : NekoModulePipeline.class.getDeclaredConstructors()) {
            assertPublicSignatureClean(ctor.getParameterTypes());
        }
        for (Method method : NekoModulePipeline.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            assertPublicSignatureClean(method.getParameterTypes());
            assertPublicSignatureClean(method.getReturnType());
        }
    }

    private static void assertPublicSignatureClean(Class<?>... types) {
        for (Class<?> type : types) {
            String name = type.getName();
            assertTrue(!name.contains("NekoLexer") && !name.contains("NekoParser")
                            && !name.contains("NekoAstLowering") && !name.contains("NekoTokenStream")
                            && !name.contains("NekoSourceAst"),
                    "管线公开签名不得出现 parser SPI: " + name);
        }
    }
}
