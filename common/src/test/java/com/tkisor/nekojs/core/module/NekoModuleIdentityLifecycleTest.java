package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.core.ScriptFilePolicy;
import com.tkisor.nekojs.core.compiler.IScriptCompiler;
import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.NekoTypeScriptLanguagePlugin;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.SourceMapRegistry;
import com.tkisor.nekojs.core.fs.NekoJSFileSystem;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.fs.SandboxPolicy;
import com.tkisor.nekojs.core.module.esm.NekoEsmVirtualModuleRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.PolyglotException;
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
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

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
    private NekoModulePipelineCache cache;
    private ScriptCompilerRegistry compilers;

    @BeforeEach
    void setUp() throws Exception {
        TestPlatformInit.ensureInitialized(gameDir);
        paths = pathsFor(gameDir);
        Files.createDirectories(paths.serverScripts().resolve("src"));
        compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        compilers.register(NekoTypeScriptLanguagePlugin.INSTANCE);
        cache = new NekoModulePipelineCache(new NekoModulePipeline(
                new NekoCompilationPipeline(), compilers, SandboxConfig.defaultConfig()),
                new SourceMapRegistry(paths.root()), new NekoEsmVirtualModuleRegistry(paths.root()),
                NekoTrustContext.local());
        // 生产级接线：虚拟 ESM 模块经 NekoJSFileSystem 由 guest import 解析（与 NekoSandboxFactory 一致）。
        IOAccess ioAccess = IOAccess.newBuilder()
                .fileSystem(new NekoJSFileSystem(paths.root(),
                        new SandboxPolicy(SandboxConfig.defaultConfig(), paths), paths, cache))
                .build();
        context = Context.newBuilder("js").allowAllAccess(true).allowIO(ioAccess).build();
        host = new NekoScriptModuleLoaderHost(
                context, new NekoModuleResolver(paths.gameDir(), paths.root(), paths.nodeModules(),
                        new ScriptFilePolicy(compilers)), cache);
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
    void requireMissingBarePackageReportsModuleNotFoundBeforeSpecialExecution() throws Exception {
        Path entry = paths.serverScripts().resolve("src/missing-package-entry.cjs");
        Files.writeString(entry, "module.exports = require('missing-package');\n");

        IOException failure = assertThrows(IOException.class,
                () -> host.loadEntry("./server_scripts/src/missing-package-entry.cjs"));

        NekoModuleError staged = NekoModulePipelinePrepareTest.assertStaged(
                failure, NekoModuleError.Stage.RESOLVE, NekoModuleError.OWNER_RESOLUTION_CACHE);
        assertEquals("missing-package", staged.moduleId());
        assertTrue(staged.detail().contains("missing-package"));
        assertTrue(!staged.detail().contains("special"), "missing bare packages must not enter SPECIAL resolution");
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
    void changedCjsSourceInvalidatesExportsWithoutExplicitInvalidate() throws Exception {
        Path entry = paths.serverScripts().resolve("src/stale-cjs.cjs");
        Files.writeString(entry, "module.exports = 'AAAA';\n");

        Value first = asValue(host.loadEntry("./server_scripts/src/stale-cjs.cjs"));
        assertEquals("AAAA", first.asString());

        Files.writeString(entry, "module.exports = 'BBBB';\n");

        Value second = asValue(host.loadEntry("./server_scripts/src/stale-cjs.cjs"));
        assertEquals("BBBB", second.asString(),
                "a changed prepared identity must not return the old CJS exports cache");
    }

    @Test
    void changedLanguageIdentityInvalidatesCjsExportsWithoutExplicitInvalidate() throws Exception {
        Path entry = paths.serverScripts().resolve("src/language-identity.js");
        Files.writeString(entry, "module.exports = 'native';\n");

        Value first = asValue(host.loadEntry("./server_scripts/src/language-identity.js"));
        assertEquals("native", first.asString());

        compilers.registerLanguage("legacy-language-identity", Set.of(".js"), new IScriptCompiler() {
            @Override
            public boolean canCompile(String extension) {
                return ".js".equalsIgnoreCase(extension);
            }

            @Override
            public String compile(Path file, String sourceCode) {
                return "module.exports = 'legacy';\n";
            }
        });

        Value second = asValue(host.loadEntry("./server_scripts/src/language-identity.js"));
        assertEquals("legacy", second.asString(),
                "a changed language identity must not return the old CJS exports cache");
    }

    @Test
    void changedEsmSourceInvalidatesNamespaceWithoutExplicitInvalidate() throws Exception {
        Path entry = paths.serverScripts().resolve("src/stale-esm.mjs");
        Files.writeString(entry, "export const value = 'AAAA';\n");

        Value first = asValue(host.loadEntry("./server_scripts/src/stale-esm.mjs"));
        assertEquals("AAAA", first.getMember("value").asString());

        Files.writeString(entry, "export const value = 'BBBB';\n");

        Value second = asValue(host.loadEntry("./server_scripts/src/stale-esm.mjs"));
        assertEquals("BBBB", second.getMember("value").asString(),
                "a changed prepared identity must not return the old ESM namespace");
    }

    @Test
    void changedStaticEsmChildInvalidatesParentWithoutExplicitInvalidate() throws Exception {
        Path dir = paths.serverScripts().resolve("src");
        Path child = dir.resolve("static-child.mjs");
        Path entry = dir.resolve("static-entry.mjs");
        Files.writeString(child, "export const value = 'AAAA';\n");
        Files.writeString(entry, "import { value } from './static-child.mjs';\nexport { value };\n");

        Value first = asValue(host.loadEntry("./server_scripts/src/static-entry.mjs"));
        assertEquals("AAAA", first.getMember("value").asString());

        Files.writeString(child, "export const value = 'BBBB';\n");

        Value second = asValue(host.loadEntry("./server_scripts/src/static-entry.mjs"));
        assertEquals("BBBB", second.getMember("value").asString(),
                "a changed static ESM child must invalidate the parent execution cache");
    }

    @Test
    void changedDynamicJsonChildInvalidatesParentAndRetainsDependencyPath() throws Exception {
        Path dir = paths.serverScripts().resolve("src");
        Path child = dir.resolve("dynamic-data.json");
        Path entry = dir.resolve("dynamic-json-entry.mjs");
        Files.writeString(child, "{\"value\":\"AAAA\"}\n");
        Files.writeString(entry, "const pending = await import('./dynamic-data.json');\n"
                + "export const value = (await pending).default.value;\n");

        Value first = asValue(host.loadEntryAsync("./server_scripts/src/dynamic-json-entry.mjs")
                .get(10, TimeUnit.SECONDS));
        assertEquals("AAAA", first.getMember("value").asString());
        assertTrue(host.affectedEntries("./server_scripts/src/dynamic-data.json")
                        .contains("server_scripts/src/dynamic-json-entry.mjs"),
                "dynamic JSON resolution must be retained in the dependency graph");

        Files.writeString(child, "{\"value\":\"BBBB\"}\n");

        Value second = asValue(host.loadEntryAsync("./server_scripts/src/dynamic-json-entry.mjs")
                .get(10, TimeUnit.SECONDS));
        assertEquals("BBBB", second.getMember("value").asString(),
                "a changed dynamic JSON child must invalidate the parent execution cache");
    }

    @Test
    void esmCycleUsesExistingLinkAndEvaluationSemantics() throws Exception {
        Path dir = paths.serverScripts().resolve("src");
        Files.writeString(dir.resolve("cycle-a.mjs"),
                "import { bValue } from './cycle-b.mjs';\n"
                        + "export function getA() { return 'a'; }\n"
                        + "export const fromB = bValue;\n");
        Files.writeString(dir.resolve("cycle-b.mjs"),
                "import { getA } from './cycle-a.mjs';\n"
                        + "export const bValue = 'b';\n"
                        + "export const fromA = getA();\n");

        Value namespace = asValue(host.loadEntry("./server_scripts/src/cycle-a.mjs"));

        assertEquals("b", namespace.getMember("fromB").asString());
    }

    @Test
    void literalDynamicImportResolvesAtRuntimeRecordsDependencyAndInvalidatesParent() throws Exception {
        Path dir = paths.serverScripts().resolve("src");
        Path child = dir.resolve("dynamic-child.mjs");
        Path entry = dir.resolve("dynamic-entry.mjs");
        Files.writeString(child, "export const value = 'AAAA';\n");
        Files.writeString(entry, "const pending = await import('./dynamic-child.mjs');\n"
                + "export const value = pending.value;\n");

        Value first = asValue(host.loadEntryAsync("./server_scripts/src/dynamic-entry.mjs")
                .get(10, TimeUnit.SECONDS));
        assertEquals("AAAA", first.getMember("value").asString());

        Files.writeString(child, "export const value = 'BBBB';\n");

        Value second = asValue(host.loadEntryAsync("./server_scripts/src/dynamic-entry.mjs")
                .get(10, TimeUnit.SECONDS));
        assertEquals("BBBB", second.getMember("value").asString(),
                "a changed dynamic child must invalidate the parent execution cache");
    }

    @Test
    void literalDynamicImportResolutionFailureKeepsResolveStageAndCause() throws Exception {
        Path entry = paths.serverScripts().resolve("src/dynamic-missing.mjs");
        Files.writeString(entry, "const pending = await import('./does-not-exist.mjs');\n"
                + "export { pending };\n");

        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> host.loadEntryAsync("./server_scripts/src/dynamic-missing.mjs")
                        .get(10, TimeUnit.SECONDS));

        NekoModuleError staged = NekoModulePipelinePrepareTest.assertStaged(
                failure, NekoModuleError.Stage.RESOLVE, NekoModuleError.OWNER_RESOLUTION_CACHE);
        assertEquals("./does-not-exist.mjs", staged.moduleId());
        assertTrue(staged.sourcePath().replace('\\', '/').endsWith("dynamic-missing.mjs"), staged.detail());
        assertNotNull(staged.getCause(), "literal dynamic import must retain resolver I/O cause");
    }

    @Test
    void literalDynamicImportLinkFailureKeepsLinkStageAndCause() throws Exception {
        Path dir = paths.serverScripts().resolve("src");
        Files.writeString(dir.resolve("dynamic-bad.mjs"),
                "export const value = 1;\nexport const value = 2;\n");
        Files.writeString(dir.resolve("dynamic-link-entry.mjs"),
                "const pending = await import('./dynamic-bad.mjs');\n"
                        + "export { pending };\n");

        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> host.loadEntryAsync("./server_scripts/src/dynamic-link-entry.mjs")
                        .get(10, TimeUnit.SECONDS));

        NekoModuleError staged = NekoModulePipelinePrepareTest.assertStaged(
                failure, NekoModuleError.Stage.LINK, NekoModuleError.OWNER_RESOLUTION_CACHE);
        assertNotNull(staged.getCause(), "literal dynamic import link failure must retain its cause");
        assertTrue(String.valueOf(staged.sourcePath()).replace('\\', '/').endsWith("dynamic-bad.mjs"), staged.detail());
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
    void separateHostsKeepVirtualSourcesAndSourceMapsPrivate() throws Exception {
        NekoModulePipelineCache otherCache = new NekoModulePipelineCache(
                new NekoModulePipeline(new NekoCompilationPipeline(),
                        ScriptCompilerRegistry.createRuntimeRegistry(), SandboxConfig.defaultConfig()),
                new SourceMapRegistry(paths.root()), new NekoEsmVirtualModuleRegistry(paths.root()),
                NekoTrustContext.local());
        try (Context otherContext = Context.newBuilder("js").allowAllAccess(true).build()) {
            NekoScriptModuleLoaderHost otherHost = new NekoScriptModuleLoaderHost(otherContext,
                    new NekoModuleResolver(paths.gameDir(), paths.root(), paths.nodeModules(),
                            ScriptFilePolicy.legacyRuntime()), otherCache);
            String moduleId = "server_scripts/src/host-isolation.mjs";
            Path firstVirtual = Path.of(cache.virtualModules().register(moduleId, "export const owner = 'first';"));
            Path secondVirtual = Path.of(otherCache.virtualModules().register(moduleId, "export const owner = 'second';"));

            String firstMap = sourceMapWithContent("server_scripts/src/host-isolation.ts", "first source");
            String secondMap = sourceMapWithContent("server_scripts/src/host-isolation.ts", "second source");
            cache.sourceMaps().register("server_scripts/src/host-isolation.ts", firstMap, 0);
            otherCache.sourceMaps().register("server_scripts/src/host-isolation.ts", secondMap, 0);

            assertEquals(firstVirtual, secondVirtual, "the virtual URI is deterministic across hosts");
            assertTrue(cache.virtualModules().source(firstVirtual).contains("first"));
            assertTrue(otherCache.virtualModules().source(secondVirtual).contains("second"));
            assertEquals("first source", cache.sourceMaps().getMappedPosition(
                    "server_scripts/src/host-isolation.ts", 1, 1).sourceContent);
            assertEquals("second source", otherCache.sourceMaps().getMappedPosition(
                    "server_scripts/src/host-isolation.ts", 1, 1).sourceContent);

            cache.clear();

            assertTrue(otherCache.virtualModules().source(secondVirtual).contains("second"));
            assertEquals("second source", otherCache.sourceMaps().getMappedPosition(
                    "server_scripts/src/host-isolation.ts", 1, 1).sourceContent);
        } finally {
            otherCache.clear();
        }
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

        NekoModuleError staged = NekoModulePipelinePrepareTest.assertStaged(
                failure, NekoModuleError.Stage.LINK, NekoModuleError.OWNER_RESOLUTION_CACHE);
        com.tkisor.nekojs.core.module.esm.NekoEsmLinkException link =
                assertInstanceOf(com.tkisor.nekojs.core.module.esm.NekoEsmLinkException.class, staged.getCause());
        assertNotNull(link.diagnostic().file(), "link 失败必须携带源文件");
        assertTrue(link.diagnostic().file().toString().replace('\\', '/').endsWith("dup-entry.mjs"),
                "was: " + link.diagnostic());
    }

    @Test
    void crossImportRuntimeErrorKeepsModuleIdentity() throws Exception {
        Path dir = paths.serverScripts().resolve("src");
        Files.writeString(dir.resolve("exec-inner.cjs"), "throw new Error('leaf-boom');\n");
        Files.writeString(dir.resolve("exec-outer.cjs"), "require('./exec-inner.cjs');\n");

        IOException failure = assertThrows(IOException.class,
                () -> host.loadEntry("./server_scripts/src/exec-outer.cjs"));

        NekoModuleError staged = NekoModulePipelinePrepareTest.assertStaged(
                failure, NekoModuleError.Stage.EXECUTE, NekoModuleError.OWNER_EXECUTION);
        assertTrue(String.valueOf(staged.getMessage()).contains("leaf-boom"), String.valueOf(staged));
        // Native CJS also publishes an identity source map; sourceURL remains an execution fallback.
        String stack = stackText(staged);
        assertTrue(stack.contains("exec-inner.cjs"), "跨 import 错误栈必须保留来源模块身份: " + stack);
        assertTrue(stack.matches("(?s).*exec-inner\\.cjs:\\d+.*"),
                "跨 import 错误栈必须保留来源行列: " + stack);
    }

    private static String stackText(Throwable failure) {
        java.io.StringWriter writer = new java.io.StringWriter();
        failure.printStackTrace(new java.io.PrintWriter(writer));
        return writer.toString().replace('\\', '/');
    }

    private static String sourceMapWithContent(String sourcePath, String content) {
        return "{\"version\":3,\"file\":\"host-isolation.js\",\"sources\":[\"" + sourcePath
                + "\"],\"sourcesContent\":[\"" + content
                + "\"],\"names\":[],\"mappings\":\"AAAA\"}";
    }

    @Test
    void loadEntryRuntimeFailureKeepsTranspiledModuleSourceLocation() throws Exception {
        Path dir = paths.serverScripts().resolve("src");
        Path ts = dir.resolve("map-leaf.ts");
        String source = "const label: string = 'leaf';\n"
                + "throw new Error('ts-leaf-boom');\n"
                + "export const out: number = 42;\n";
        Files.writeString(ts, source);

        IOException failure = assertThrows(IOException.class,
                () -> host.loadEntry("./server_scripts/src/map-leaf.ts"));

        NekoModuleError staged = NekoModulePipelinePrepareTest.assertStaged(
                failure, NekoModuleError.Stage.EXECUTE, NekoModuleError.OWNER_EXECUTION);
        PolyglotException guestFailure = assertInstanceOf(PolyglotException.class, staged.getCause());
        assertTrue(staged.getMessage().contains("ts-leaf-boom"), String.valueOf(staged));
        assertEquals("server_scripts/src/map-leaf.ts", staged.sourcePath().replace('\\', '/'));
        assertEquals(2, staged.sourceLine(), "the exposed error must carry the authored line");
        assertTrue(staged.sourceColumn() > 0, "the exposed error must carry the authored column");
        assertEquals("server_scripts/src/map-leaf.ts", staged.moduleId().replace('\\', '/'));
        assertNotNull(guestFailure, "the exposed diagnostic must retain the guest cause");
    }

    @Test
    void crossImportTranspiledRuntimeFailureExposesAuthoredLocationAtLoadEntry() throws Exception {
        Path dir = paths.serverScripts().resolve("src");
        Path inner = dir.resolve("map-import-inner.ts");
        Path outer = dir.resolve("map-import-outer.ts");
        Files.writeString(inner, "const label: string = 'inner';\n"
                + "throw new Error('ts-import-boom');\n"
                + "export const out: number = 42;\n");
        Files.writeString(outer, "import { out } from './map-import-inner.ts';\n"
                + "export const value = out;\n");

        IOException failure = assertThrows(IOException.class,
                () -> host.loadEntry("./server_scripts/src/map-import-outer.ts"));

        NekoModuleError staged = NekoModulePipelinePrepareTest.assertStaged(
                failure, NekoModuleError.Stage.EXECUTE, NekoModuleError.OWNER_EXECUTION);
        assertTrue(staged.getMessage().contains("ts-import-boom"), String.valueOf(staged));
        assertEquals("server_scripts/src/map-import-inner.ts", staged.sourcePath().replace('\\', '/'), staged.detail());
        assertEquals(2, staged.sourceLine(), staged.detail());
        assertTrue(staged.sourceColumn() > 0, staged.detail());
        assertEquals("server_scripts/src/map-import-inner.ts", staged.moduleId().replace('\\', '/'), staged.detail());
        assertNotNull(staged.getCause());
    }

    @Test
    void rewrittenStaticImportRuntimeFailureMapsToAuthoredLocation() throws Exception {
        Path dir = paths.serverScripts().resolve("src");
        Files.writeString(dir.resolve("map-static-child.mjs"), "export const value = 1;\n");
        Path entry = dir.resolve("map-static-entry.mjs");
        String source = "import { value } from './map-static-child.mjs'; throw new Error('static-rewrite-boom');\n";
        Files.writeString(entry, source);

        IOException failure = assertThrows(IOException.class,
                () -> host.loadEntry("./server_scripts/src/map-static-entry.mjs"));

        NekoModuleError staged = NekoModulePipelinePrepareTest.assertStaged(
                failure, NekoModuleError.Stage.EXECUTE, NekoModuleError.OWNER_EXECUTION);
        assertEquals("server_scripts/src/map-static-entry.mjs", staged.sourcePath().replace('\\', '/'));
        assertEquals(1, staged.sourceLine(), staged.detail());
        assertTrue(staged.sourceColumn() > 0 && staged.sourceColumn() <= source.stripTrailing().length(),
                staged.detail());
        assertNotNull(staged.getCause());
    }

    @Test
    void rewrittenDynamicImportRuntimeFailureMapsToAuthoredLocation() throws Exception {
        Path dir = paths.serverScripts().resolve("src");
        Files.writeString(dir.resolve("map-dynamic-child.mjs"), "export const value = 1;\n");
        Path entry = dir.resolve("map-dynamic-entry.mjs");
        String source = "const child = await import('./map-dynamic-child.mjs'); throw new Error('dynamic-rewrite-boom');\n";
        Files.writeString(entry, source);

        java.util.concurrent.ExecutionException failure = assertThrows(java.util.concurrent.ExecutionException.class,
                () -> host.loadEntryAsync("./server_scripts/src/map-dynamic-entry.mjs")
                        .get(10, java.util.concurrent.TimeUnit.SECONDS));

        NekoModuleError staged = NekoModulePipelinePrepareTest.assertStaged(
                failure, NekoModuleError.Stage.EXECUTE, NekoModuleError.OWNER_EXECUTION);
        assertEquals("server_scripts/src/map-dynamic-entry.mjs", staged.sourcePath().replace('\\', '/'));
        assertEquals(1, staged.sourceLine(), staged.detail());
        assertTrue(staged.sourceColumn() > 0 && staged.sourceColumn() <= source.stripTrailing().length(),
                staged.detail());
        assertNotNull(staged.getCause());
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
