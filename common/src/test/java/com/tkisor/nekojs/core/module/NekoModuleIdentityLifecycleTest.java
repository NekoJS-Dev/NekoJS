package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.core.ScriptFilePolicy;
import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.NekoTypeScriptLanguagePlugin;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.SourceMapRegistry;
import com.tkisor.nekojs.core.fs.NekoJSFileSystem;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.fs.SandboxPolicy;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Source;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.io.IOAccess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 票据 11 AC3 + AC7：CJS require/module.exports 与 ESM import/export/link 的模块身份
 * 在重复加载、循环依赖、跨入口调用下保持既有语义；跨 import 的执行错误回到原始文件与模块身份。
 *
 * <p>经最高调用者 seam（{@link NekoScriptModuleLoaderHost#loadEntry} /
 * {@code requireFrom} / {@code nativeImport}）观察行为。
 */
class NekoModuleIdentityLifecycleTest {
    @TempDir
    Path gameDir;

    private NekoJSPaths paths;
    private Context context;
    private NekoScriptModuleLoaderHost host;

    @BeforeEach
    void setUp() throws Exception {
        TestPlatformInit.ensureInitialized(gameDir);
        paths = pathsFor(gameDir);
        Files.createDirectories(paths.serverScripts().resolve("src"));
        NekoModulePipelineCache cache = NekoModulePipelineCache.withExplicitPipeline(
                ScriptCompilerRegistry.createRuntimeRegistry(), SandboxConfig.defaultConfig());
        // 生产级接线：虚拟 ESM 模块经 NekoJSFileSystem 由 guest import 解析（与 NekoSandboxFactory 一致）。
        IOAccess ioAccess = IOAccess.newBuilder()
                .fileSystem(new NekoJSFileSystem(paths.root(),
                        new SandboxPolicy(SandboxConfig.defaultConfig(), paths), cache))
                .build();
        context = Context.newBuilder("js").allowAllAccess(true).allowIO(ioAccess).build();
        host = new NekoScriptModuleLoaderHost(
                context, new NekoModuleResolver(paths, ScriptFilePolicy.legacyRuntime()), paths, cache);
        context.getBindings("js").putMember("__nekoScriptModuleLoaderHost", host);
        try (var in = getClass().getResourceAsStream("/nekojs/node/internal/script-loader.js")) {
            assertNotNull(in, "script-loader.js must be on the test classpath");
            String loader = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            context.eval(Source.newBuilder("js", loader, "nekojs/node/internal/script-loader.js").build());
        }
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void repeatedRequireReturnsIdenticalExports() throws Exception {
        Path dir = paths.serverScripts().resolve("src");
        Files.writeString(dir.resolve("id-dep.cjs"), "module.exports = { tag: 'dep' };\n");
        Files.writeString(dir.resolve("id-entry.cjs"),
                "const a = require('./id-dep.cjs');\n"
                        + "const b = require('./id-dep.cjs');\n"
                        + "module.exports = { same: a === b, tag: a.tag };\n");

        Value exports = asValue(host.loadEntry("./server_scripts/src/id-entry.cjs"));

        assertTrue(exports.getMember("same").asBoolean(), "重复 require 必须返回同一模块身份");
        assertEquals("dep", exports.getMember("tag").asString());
    }

    @Test
    void circularRequireTerminatesWithPartialExports() throws Exception {
        Path dir = paths.serverScripts().resolve("src");
        Files.writeString(dir.resolve("circ-a.cjs"),
                "exports.a = 1;\n"
                        + "const b = require('./circ-b.cjs');\n"
                        + "exports.bSeen = b.b;\n");
        Files.writeString(dir.resolve("circ-b.cjs"),
                "exports.b = 2;\n"
                        + "const a = require('./circ-a.cjs');\n"
                        + "exports.aSeen = a.a;\n");
        Files.writeString(dir.resolve("circ-entry.cjs"),
                "const a = require('./circ-a.cjs');\n"
                        + "const b = require('./circ-b.cjs');\n"
                        + "module.exports = { bSeen: a.bSeen, aSeen: b.aSeen };\n");

        // Node 循环语义：执行前先入缓存，循环方拿到部分 exports；此处不断言 StackOverflow。
        Value exports = asValue(host.loadEntry("./server_scripts/src/circ-entry.cjs"));

        assertEquals(2, exports.getMember("bSeen").asInt());
        assertEquals(1, exports.getMember("aSeen").asInt());
    }

    @Test
    void sharedDependencyEvaluatesOnceAcrossEntries() throws Exception {
        Path dir = paths.serverScripts().resolve("src");
        Files.writeString(dir.resolve("shared-dep.cjs"),
                "globalThis.__sharedDepLoads = (globalThis.__sharedDepLoads || 0) + 1;\n"
                        + "module.exports = {};\n");
        Files.writeString(dir.resolve("shared-entry-one.cjs"), "require('./shared-dep.cjs');\nmodule.exports = 1;\n");
        Files.writeString(dir.resolve("shared-entry-two.cjs"), "require('./shared-dep.cjs');\nmodule.exports = 2;\n");

        host.loadEntry("./server_scripts/src/shared-entry-one.cjs");
        host.loadEntry("./server_scripts/src/shared-entry-two.cjs");

        Value loads = context.eval("js", "globalThis.__sharedDepLoads");
        assertEquals(1, loads.asInt(), "跨入口共享依赖只求值一次（同一模块身份）");
    }

    @Test
    void esmNamespaceIdentityHoldsAcrossImports() throws Exception {
        Path dir = paths.serverScripts().resolve("src");
        Files.writeString(dir.resolve("ns-dep.mjs"), "export const tag = 'ns';\n");
        Files.writeString(dir.resolve("ns-entry-one.mjs"),
                "import * as ns from './ns-dep.mjs';\n"
                        + "globalThis.__nsOne = ns;\n"
                        + "export const tag = ns.tag;\n");
        Files.writeString(dir.resolve("ns-entry-two.mjs"),
                "import * as ns from './ns-dep.mjs';\n"
                        + "globalThis.__nsTwo = ns;\n"
                        + "export const tag = ns.tag;\n");

        Value first = asValue(host.loadEntry("./server_scripts/src/ns-entry-one.mjs"));
        Value second = asValue(host.loadEntry("./server_scripts/src/ns-entry-two.mjs"));

        assertEquals("ns", first.getMember("tag").asString());
        assertEquals("ns", second.getMember("tag").asString());
        Value identical = context.eval("js", "globalThis.__nsOne === globalThis.__nsTwo");
        assertTrue(identical.asBoolean(), "同一 ESM 模块的命名空间身份必须一致");
    }

    @Test
    void esmDuplicateExportFailsLinkWithLocation() throws Exception {
        Path dir = paths.serverScripts().resolve("src");
        Files.writeString(dir.resolve("dup-entry.mjs"),
                "export const value = 1;\n"
                        + "export const value = 2;\n");

        IOException failure = assertThrows(IOException.class,
                () -> host.loadEntry("./server_scripts/src/dup-entry.mjs"));

        // LINK 阶段按类型区分（NekoEsmLinkException 自带 file/line/column 诊断）。
        com.tkisor.nekojs.core.module.esm.NekoEsmLinkException link =
                assertInstanceOf(com.tkisor.nekojs.core.module.esm.NekoEsmLinkException.class, failure);
        assertNotNull(link.diagnostic().file(), "link 失败必须携带源文件");
        assertTrue(link.diagnostic().file().toString().replace('\\', '/').endsWith("dup-entry.mjs"),
                "was: " + link.diagnostic());
    }

    @Test
    void crossImportRuntimeErrorKeepsModuleIdentity() throws Exception {
        Path dir = paths.serverScripts().resolve("src");
        Files.writeString(dir.resolve("exec-inner.cjs"), "throw new Error('leaf-boom');\n");
        Files.writeString(dir.resolve("exec-outer.cjs"), "require('./exec-inner.cjs');\n");

        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> host.loadEntry("./server_scripts/src/exec-outer.cjs"));

        // CJS 模块以 sourceURL=模块 id 求值：guest 栈帧携带来源模块身份（message 只有错误文本）。
        String message = String.valueOf(failure.getMessage());
        assertTrue(message.contains("leaf-boom"), "message was: " + message);
        String stack = stackText(failure);
        assertTrue(stack.contains("exec-inner.cjs"), "跨 import 错误栈必须保留来源模块身份: " + stack);
        assertTrue(stack.matches("(?s).*exec-inner\\.cjs:\\d+.*"),
                "跨 import 错误栈必须保留来源行列: " + stack);
    }

    private static String stackText(Throwable failure) {
        java.io.StringWriter writer = new java.io.StringWriter();
        failure.printStackTrace(new java.io.PrintWriter(writer));
        return writer.toString().replace('\\', '/');
    }

    @Test
    void transpiledModuleErrorMapsBackToOriginalFileViaSourceMap() throws Exception {
        // TS 擦除产物经 source map 回到原始 .ts 文件行列：prepare 发布映射，错误侧按映射归因。
        ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        compilers.register(NekoTypeScriptLanguagePlugin.INSTANCE);
        NekoModulePipeline pipeline = new NekoModulePipeline(
                new NekoCompilationPipeline(), compilers, SandboxConfig.defaultConfig());
        Path dir = paths.serverScripts().resolve("src");
        Path ts = dir.resolve("map-leaf.ts");
        String source = "const label: string = 'leaf';\n"
                + "const hit: number = 41 + 1;\n"
                + "export const out = hit;\n";
        Files.writeString(ts, source);

        NekoPreparedModule prepared = pipeline.prepare(ts, source);
        assertEquals("typescript", prepared.languageId());
        assertNotNull(prepared.sourceMap(), "转译模块必须携带可用 source map");

        String relative = paths.root().relativize(ts.toAbsolutePath().normalize())
                .toString().replace('\\', '/');
        SourceMapRegistry.register(relative, prepared.sourceMap(), prepared.prependedLineCount());
        try {
            // 注：SourceMapRegistry/NekoJSPaths 根为进程先赢单例，多测试同 JVM 时 map 内嵌的
            // sources[0] 可能是绝对路径回退；生产单根下恒为 root-relative。此处以后缀判定同一原始文件。
            boolean mapped = false;
            for (int line = 1; line <= 3 && !mapped; line++) {
                SourceMapRegistry.OriginalPosition position =
                        SourceMapRegistry.getMappedPosition(relative, line, 1);
                if (position.path != null && position.path.replace('\\', '/').endsWith(relative)) {
                    mapped = true;
                    assertEquals(line, position.line, "恒等映射行列一致");
                }
            }
            assertTrue(mapped, "source map 必须能映射回原始 " + relative);
        } finally {
            SourceMapRegistry.clear(relative);
        }
    }

    private static Value asValue(Object exports) {
        assertTrue(exports instanceof Value, "host 应返回 guest Value, was: " + exports);
        return (Value) exports;
    }

    private static NekoJSPaths pathsFor(Path gameDir) throws Exception {
        Constructor<NekoJSPaths> constructor = NekoJSPaths.class.getDeclaredConstructor(Path.class);
        constructor.setAccessible(true);
        return constructor.newInstance(gameDir);
    }
}
