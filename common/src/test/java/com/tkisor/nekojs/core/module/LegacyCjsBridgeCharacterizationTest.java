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
import com.tkisor.nekojs.core.module.esm.NekoEsmVirtualModuleRegistry;
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
 * 票据 11 AC9：legacy CJS bridge 的 characterization、当前保留原因与收缩 gate。
 *
 * <p>本票所指 legacy CJS bridge = 外部语言经 {@link IScriptCompiler} 注册后走
 * {@code compileDetailed → CJS 静态分析} 的兼容输入路径（见
 * {@link NekoModulePipeline} 的 legacy 分支），以及 node 内建资源直装路径
 * （{@link NekoNodeModuleInstaller} 的 manifest 求值，不经过管线）。
 * 两者都被显式表征；保留原因与删除条件见类注与迁移材料，删除不推迟到 final release
 * 统一清理（条件满足即删），同一公开语义没有第二条长期 pipeline。
 *
 * <p>保留原因：
 * <ul>
 *   <li>外部语言插件兼容：已发布的语言以后缀注册参与管线是公开扩展能力，
 *       删除即删除公开语言参与路径（票据约束“不删公开语言”）；</li>
 *   <li>node 内建资源（classpath manifest）语义不同于用户模块（无文件身份/缓存/
 *       reload），直装是独立语义而非第二条用户管线。</li>
 * </ul>
 *
 * <p>删除条件（收缩 gate，须全部满足；满足后随票删除，不进 final release 统一清理）：
 * <ol>
 *   <li>替代 behavior：同后缀经内置语言插件产出逐字节一致的 code/mode（corpus 对照）；</li>
 *   <li>declaration：managed 声明面覆盖该后缀的模块能力；</li>
 *   <li>trace：全仓无 {@code IScriptCompiler} 该后缀注册（grep 证据）；</li>
 *   <li>无调用者：legacy 分支无生产/测试调用（覆盖率证据）。</li>
 * </ol>
 */
class LegacyCjsBridgeCharacterizationTest {

    @BeforeAll
    static void bindPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    /** 外部语言经 legacy 编译器参与：产物走 CJS 语义，分析跑在编译产物上。 */
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
                // 模拟转译：生成 require/module.exports（原始源里不存在）。
                return "const other = require('./other.upperjs');\n"
                        + "module.exports = { lang: 'upper', dep: other };\n";
            }
        });
        // ESM 编排关闭时 legacy 后缀走直接 CJS 分支（compileDetailed → CJS 分析）。
        SandboxConfig cjsOnly = new SandboxConfig(false, false, false, false, false, true, false,
                false, 30, 0, 0);
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

    /** legacy 路径同样复用同一 prepare 产物形态：没有第二条 pipeline。 */    @Test
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
                false, 30, 0, 0);
        NekoModulePipeline pipeline = new NekoModulePipeline(
                new NekoCompilationPipeline(), compilers, cjsOnly);

        NekoPreparedModule legacy = pipeline.prepare(Path.of("x/a.idemjs"), "module.exports = 1;\n");
        NekoPreparedModule nativeJs = pipeline.prepare(Path.of("x/a.cjs"), "module.exports = 1;\n");

        assertEquals(nativeJs.mode(), legacy.mode(), "同为 CJS mode");
        assertEquals(nativeJs.code(), legacy.code(), "同源经 legacy 与原生路径产物一致");
        assertNotNull(legacy.cacheKey());
        assertNotNull(nativeJs.cacheKey());
    }

    /** node 内建资源直装路径：不经过管线（独立语义：classpath 资源，无文件身份/缓存）。 */
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

    /** legacy 后缀经同一 Resolution/Cache 路径装载执行：require 身份与 CJS 一致。 */
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

    /** 收缩 gate 结构证据：管线不再持有 static 可变状态（旧 LEGACY_INSTANCE/SHARED 已删）。 */
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

    /** 收缩 gate 结构证据：prepared 缓存无 process-wide static（实例由 owner 持有）。 */
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

    /** 不新增公共 parser ModuleSPI：管线公开签名只出现 pipeline/registry/config 与产物类型。 */
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
