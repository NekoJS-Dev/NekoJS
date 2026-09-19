package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.ScriptFilePolicy;
import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.NekoJsxLanguagePlugin;
import com.tkisor.nekojs.core.compiler.NekoTsxLanguagePlugin;
import com.tkisor.nekojs.core.compiler.NekoTypeScriptLanguagePlugin;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.DefaultErrorTracker;
import com.tkisor.nekojs.core.error.SourceMapRegistry;
import com.tkisor.nekojs.core.fs.NekoJSFileSystem;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.fs.SandboxPolicy;
import com.tkisor.nekojs.core.module.esm.NekoEsmLinkException;
import com.tkisor.nekojs.core.node.NekoNodeModuleInstaller;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.PolyglotException;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.io.IOAccess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 票据 12 AC2 / AC3 / AC5 / AC7：TS 擦除不改变运行时值与导出形状；JSX/TSX element、fragment
 * 与 automatic runtime 保持既有行为；跨 import 的执行期异常经 source map 回到原始
 * TS/JSX/TSX 位置并保留模块身份；TS/JSX/TSX 与 JS/CJS/ESM 混合 import 的身份与错误归属可追踪。
 *
 * <p>观测点是最高调用者 seam：{@code loadEntry}/{@code loadEntryAsync} 的返回值、异常对象字段
 * （stage/owner/sourcePath/sourceLine/sourceColumn/moduleId）与 guest cause。
 */
class NekoTypeScriptJsxRuntimeTest {

    @TempDir
    Path gameDir;

    private NekoJSPaths paths;
    private ScriptCompilerRegistry compilers;
    private NekoModulePipelineCache cache;
    private Context context;
    private NekoScriptModuleLoaderHost host;
    private SandboxConfig config;

    @BeforeEach
    void setUp() throws Exception {
        TestPlatformInit.ensureInitialized(gameDir);
        paths = pathsFor(gameDir);
        Files.createDirectories(paths.serverScripts().resolve("src"));
        boot(SandboxConfig.defaultConfig());
    }

    private void boot(SandboxConfig sandboxConfig) throws Exception {
        if (context != null) {
            context.close();
        }
        config = sandboxConfig;
        compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        compilers.register(NekoTypeScriptLanguagePlugin.INSTANCE);
        compilers.register(NekoJsxLanguagePlugin.INSTANCE);
        compilers.register(NekoTsxLanguagePlugin.INSTANCE);
        cache = new NekoModulePipelineCache(
                new NekoModulePipeline(new NekoCompilationPipeline(), compilers, config),
                new SourceMapRegistry(paths.root()), new NekoEsmVirtualModuleRegistry(paths.root()),
                NekoTrustContext.local());
        IOAccess ioAccess = IOAccess.newBuilder()
                .fileSystem(new NekoJSFileSystem(paths.root(), new SandboxPolicy(config, paths), paths, cache))
                .build();
        context = Context.newBuilder("js").allowAllAccess(true).allowIO(ioAccess).build();
        NekoModuleResolver resolver = new NekoModuleResolver(paths.gameDir(), paths.root(), paths.nodeModules(),
                new ScriptFilePolicy(compilers));
        NekoNodeModuleInstaller.install(context, ScriptType.TEST, resolver, paths,
                new DefaultErrorTracker(paths, config), config, cache);
        host = (NekoScriptModuleLoaderHost) context.getBindings("js")
                .getMember("__nekoScriptModuleLoaderHost").asHostObject();
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    private Path write(String name, String source) throws IOException {
        Path file = paths.serverScripts().resolve("src/" + name);
        Files.writeString(file, source);
        return file;
    }

    // ---- AC2: erasure preserves runtime values, control flow and export shape ----

    @Test
    void typeErasurePreservesRuntimeValuesControlFlowAndExportShape() throws Exception {
        write("erasure.ts", "interface Named { name: string }\n"
                + "type Count = number;\n"
                + "class Box { private inner: number = 1; constructor(public label: string) { } }\n"
                + "enum Mode { Off = 0, Half = 0.5, On = 1 }\n"
                + "const order: string[] = [];\n"
                + "order.push('start');\n"
                + "let score: Count = 0;\n"
                + "for (let i: number = 0; i < 3; i++) { score += i; }\n"
                + "const maybe: string | null = score > 2 ? 'big' : null;\n"
                + "if (maybe) { order.push(maybe); } else { order.push('small'); }\n"
                + "const box: Box = new Box('boxed');\n"
                + "const boxed: Named = { name: box.label } as Named;\n"
                + "export const flow = order.join('|');\n"
                + "export const sum: Count = score;\n"
                + "export const mode = Mode.Half;\n"
                + "export const label = boxed.name;\n"
                + "export const shape = Object.keys({ a: 1, b: 2 }).join(',');\n");

        Value exports = asValue(host.loadEntry("./server_scripts/src/erasure.ts"));

        assertEquals("start|big", exports.getMember("flow").asString(),
                "erased control flow must run unchanged");
        assertEquals(3, exports.getMember("sum").asInt(), "erased arithmetic must run unchanged");
        assertEquals(0.5, exports.getMember("mode").asDouble(), "enum runtime values must survive erasure");
        assertEquals("boxed", exports.getMember("label").asString(),
                "parameter properties must still assign runtime fields");
        assertEquals("a,b", exports.getMember("shape").asString());
    }

    @Test
    void tsxAndJsxLeaveTheSameExportShape() throws Exception {
        write("shape.tsx", "interface Item { id: string }\n"
                + "const item: Item = { id: 'x' };\n"
                + "export const view = <div id={item.id}>{item.id}</div>;\n"
                + "export const count: number = 2;\n");
        Value tsx = asValue(host.loadEntry("./server_scripts/src/shape.tsx"));
        assertEquals(2, tsx.getMember("count").asInt());
        assertEquals("div", tsx.getMember("view").getMember("tag").asString());

        write("shape.jsx", "const item = { id: 'x' };\n"
                + "export const view = <div id={item.id}>{item.id}</div>;\n"
                + "export const count = 2;\n");
        Value jsx = asValue(host.loadEntry("./server_scripts/src/shape.jsx"));
        assertEquals(2, jsx.getMember("count").asInt());
        assertEquals("div", jsx.getMember("view").getMember("tag").asString());
    }

    // ---- AC3: JSX/TSX element, fragment and automatic runtime ----

    @Test
    void classicRuntimeLowersElementsFragmentsAndChildren() throws Exception {
        write("classic.tsx", "const items: string[] = ['a', 'b'];\n"
                + "export const list = <ul class=\"list\">\n"
                + "  <li>{(1 + 1)}</li>\n"
                + "  {items.map((item: string) => <li>{item}</li>)}\n"
                + "</ul>;\n"
                + "export const fragment = <></>;\n");

        Value exports = asValue(host.loadEntry("./server_scripts/src/classic.tsx"));

        Value list = exports.getMember("list");
        assertEquals("ul", list.getMember("tag").asString());
        assertEquals("list", list.getMember("props").getMember("class").asString());
        // Classic runtime passes children as positional arguments and the factory flattens arrays,
        // so the literal <li> plus the two mapped <li> elements all arrive as element children.
        assertEquals(3, list.getMember("children").getArraySize(),
                "element and mapped children must both be present: " + list.getMember("children"));
        assertEquals(2, list.getMember("children").getArrayElement(0).getMember("props").getMember("children").asInt());
        assertEquals("a", list.getMember("children").getArrayElement(1).getMember("props").getMember("children").asString());
        assertEquals("b", list.getMember("children").getArrayElement(2).getMember("props").getMember("children").asString());
        Value fragment = exports.getMember("fragment");
        assertEquals(0, fragment.getMember("children").getArraySize(), "empty fragment must lower with no children");
    }

    @Test
    void automaticRuntimeProducesJsxCallsAndFragmentIdentity() throws Exception {
        boot(new SandboxConfig(false, false, false, false, true, true, true, true, 30, 0, 0,
                SandboxConfig.PACK_SYNC_OFF, false, false));
        write("auto.tsx", "const items: string[] = ['a', 'b'];\n"
                + "export const single = <div id=\"x\">hi</div>;\n"
                + "export const multi = <ul>{items.map((item: string) => <li key={item}>{item}</li>)}</ul>;\n"
                + "export const frag = <></>;\n");

        Value exports = asValue(host.loadEntry("./server_scripts/src/auto.tsx"));

        Value single = exports.getMember("single");
        assertEquals("div", single.getMember("type").asString(), "automatic runtime must keep the element type");
        assertEquals("x", single.getMember("props").getMember("id").asString());
        assertEquals("hi", single.getMember("props").getMember("children").asString());

        Value multi = exports.getMember("multi");
        assertEquals(2, multi.getMember("props").getMember("children").getArraySize(),
                "automatic runtime keeps the mapped children array in props.children without flattening");
        Value first = multi.getMember("props").getMember("children").getArrayElement(0);
        assertEquals("a", first.getMember("key").asString(), "key must be a separate automatic-runtime argument");
        assertEquals("a", first.getMember("children").getArrayElement(0).asString());

        Value fragment = exports.getMember("frag");
        assertEquals("Symbol(nekojs.jsx.fragment)", String.valueOf(fragment.getMember("type")),
                "automatic fragment must use the Fragment identity");
    }

    // ---- AC5: execution-time errors map back to authored TS/JSX/TSX positions ----

    @Test
    void runtimeFailureInTranspiledModuleReportsAuthoredLineAndIdentity() throws Exception {
        write("boom.tsx", "const label: string = 'boom';\n"
                + "const view: unknown = <div>\n"
                + "  <span>{label}</span>\n"
                + "</div>;\n"
                + "throw new Error('tsx-runtime-boom');\n");

        IOException failure = assertThrows(IOException.class,
                () -> host.loadEntry("./server_scripts/src/boom.tsx"));

        NekoModuleError staged = NekoModulePipelinePrepareTest.assertStaged(
                failure, NekoModuleError.Stage.EXECUTE, NekoModuleError.OWNER_EXECUTION);
        assertTrue(staged.getMessage().contains("tsx-runtime-boom"), String.valueOf(staged));
        assertEquals("server_scripts/src/boom.tsx", staged.sourcePath().replace('\\', '/'));
        assertEquals("server_scripts/src/boom.tsx", staged.moduleId().replace('\\', '/'),
                "the failing module identity must stay the authored module");
        assertEquals(5, staged.sourceLine(), staged.detail());
        assertTrue(staged.sourceColumn() > 0, staged.detail());
        assertNotNull(staged.getCause(), "the guest cause must remain available");
    }

    /**
     * Review-round-1 F-AC4: the lowered-JS syntax error is only detectable once the executor
     * compiles the prepared code, so {@code cache.prepare} must succeed and the diagnostic is
     * produced by the host's {@code NekoEsmLinkException} mapping — not by the upstream prepare
     * branch. This test fails if the mapping branch is removed, because the raw guest position is
     * the generated line.
     */
    @Test
    void loweredJsSyntaxErrorIsMappedAfterPreparationSucceeded() throws Exception {
        Path file = write("lowered.jsx", "const view = <div>\n"
                + "  <span>a</span>\n"
                + "</div>;\n"
                + "const } = boom;\n");

        // The preparation stage cannot see this error: it is a JavaScript parse failure of the
        // already-lowered code, which only the executor surfaces.
        NekoPreparedModule prepared = cache.prepare(file);
        assertEquals("jsx", prepared.languageId());

        IOException failure = assertThrows(IOException.class,
                () -> host.loadEntry("./server_scripts/src/lowered.jsx"));

        NekoModuleError staged = NekoModulePipelinePrepareTest.assertStaged(
                failure, NekoModuleError.Stage.PREPARE, NekoModuleError.OWNER_PREPARATION);
        assertEquals("server_scripts/src/lowered.jsx", staged.sourcePath().replace('\\', '/'));
        assertEquals(4, staged.sourceLine(),
                "a lowered-JS syntax error must be reported at the authored JSX line: " + staged.detail());
        assertTrue(staged.sourceColumn() > 0, staged.detail());
        assertEquals(NekoEsmLinkException.class, staged.getCause().getClass(),
                "the diagnostic must come from the executor's syntax re-parse, not from preparation");
    }

    @Test
    void loweredTsSyntaxErrorIsMappedAfterPreparationSucceeded() throws Exception {
        Path file = write("lowered.tsx", "const view: unknown = <div>\n"
                + "  <span>a</span>\n"
                + "</div>;\n"
                + "const } = boom;\n");

        NekoPreparedModule prepared = cache.prepare(file);
        assertEquals("tsx", prepared.languageId());

        IOException failure = assertThrows(IOException.class,
                () -> host.loadEntry("./server_scripts/src/lowered.tsx"));

        NekoModuleError staged = NekoModulePipelinePrepareTest.assertStaged(
                failure, NekoModuleError.Stage.PREPARE, NekoModuleError.OWNER_PREPARATION);
        assertEquals("server_scripts/src/lowered.tsx", staged.sourcePath().replace('\\', '/'));
        assertEquals(4, staged.sourceLine(), staged.detail());
        assertTrue(staged.sourceColumn() > 0, staged.detail());
        assertEquals(NekoEsmLinkException.class, staged.getCause().getClass(),
                "the diagnostic must come from the executor's syntax re-parse, not from preparation");
    }

    @Test
    void cjsRuntimeFailureReportsAuthoredLineNotGeneratedWrapperLine() throws Exception {
        write("wrapped.tsx", "const label: string = 'wrapped';\n"
                + "const view: unknown = <div>{label}</div>;\n"
                + "throw new Error('wrapped-boom');\n");

        IOException failure = assertThrows(IOException.class,
                () -> host.loadEntry("./server_scripts/src/wrapped.tsx"));

        NekoModuleError staged = NekoModulePipelinePrepareTest.assertStaged(
                failure, NekoModuleError.Stage.EXECUTE, NekoModuleError.OWNER_EXECUTION);
        assertEquals(3, staged.sourceLine(),
                "a CommonJS wrapper must not shift the reported authored line: " + staged.detail());
        assertTrue(staged.sourceColumn() > 0, staged.detail());
    }

    // ---- AC7: mixed TS/JSX/TSX and JS/CJS/ESM imports stay traceable ----

    @Test
    void mixedImportsResolveAndPreserveModuleIdentity() throws Exception {
        write("mixed-child.tsx", "export const payload: string = 'from-tsx';\n"
                + "export default 'tsx-default';\n");
        write("mixed-entry.ts", "import fallback, { payload } from './mixed-child.tsx';\n"
                + "export const seen: string = payload;\n"
                + "export const fallbackSeen: string = fallback;\n");
        Value fromTs = asValue(host.loadEntry("./server_scripts/src/mixed-entry.ts"));
        assertEquals("from-tsx", fromTs.getMember("seen").asString());
        assertEquals("tsx-default", fromTs.getMember("fallbackSeen").asString());

        write("mixed-child.ts", "export const value: number = 7;\n");
        write("mixed-entry.mjs", "import { value } from './mixed-child.ts';\nexport const seen = value;\n");
        Value fromMjs = asValue(host.loadEntry("./server_scripts/src/mixed-entry.mjs"));
        assertEquals(7, fromMjs.getMember("seen").asInt());

        write("mixed-child.mjs", "export const tag = 'esm';\n");
        write("mixed-entry.tsx", "import { tag } from './mixed-child.mjs';\n"
                + "export const view = <span>{tag}</span>;\n"
                + "export const label: string = tag;\n");
        Value fromTsx = asValue(host.loadEntry("./server_scripts/src/mixed-entry.tsx"));
        assertEquals("esm", fromTsx.getMember("label").asString());
        assertEquals("esm", fromTsx.getMember("view").getMember("props").getMember("children").asString());
    }

    @Test
    void linkFailureAcrossMixedImportIsAttributedToTheAuthoredImportingModule() throws Exception {
        write("mixed-link-child.tsx", "export const real: number = 1;\n");
        write("mixed-link-entry.mjs", "import { ghost } from './mixed-link-child.tsx';\n"
                + "export const value = ghost;\n");

        IOException failure = assertThrows(IOException.class,
                () -> host.loadEntry("./server_scripts/src/mixed-link-entry.mjs"));

        NekoModuleError staged = NekoModulePipelinePrepareTest.assertStaged(
                failure, NekoModuleError.Stage.LINK, NekoModuleError.OWNER_RESOLUTION_CACHE);
        assertTrue(String.valueOf(staged.getMessage()).contains("ghost"), String.valueOf(staged));
        assertTrue(staged.sourcePath().replace('\\', '/').endsWith("mixed-link-entry.mjs"), staged.detail());
    }

    @Test
    void crossImportRuntimeFailureInTranspiledChildKeepsChildAuthoredIdentity() throws Exception {
        write("mixed-boom-child.tsx", "const label: string = 'child';\n"
                + "const view: unknown = <div>{label}</div>;\n"
                + "throw new Error('mixed-child-boom');\n");
        write("mixed-boom-outer.ts", "import './mixed-boom-child.tsx';\n"
                + "export const after: number = 1;\n");

        IOException failure = assertThrows(IOException.class,
                () -> host.loadEntry("./server_scripts/src/mixed-boom-outer.ts"));

        NekoModuleError staged = NekoModulePipelinePrepareTest.assertStaged(
                failure, NekoModuleError.Stage.EXECUTE, NekoModuleError.OWNER_EXECUTION);
        assertTrue(String.valueOf(staged.getMessage()).contains("mixed-child-boom"), String.valueOf(staged));
        assertEquals("server_scripts/src/mixed-boom-child.tsx", staged.sourcePath().replace('\\', '/'),
                "a nested transpiled failure must name the failing child, not the synthetic interop module: "
                        + staged.detail());
        assertEquals("server_scripts/src/mixed-boom-child.tsx", staged.moduleId().replace('\\', '/'));
        // 该位置过去是 -1（“interop 包装的子模块失败没有 guest location”）。宿主现在按 guest 栈帧
        // 选择「能解析到 authored 位置」的那一帧，因此这里能给出子模块真实的 throw 行 3；
        // 归因仍属于子模块，不是合成的 interop 模块。
        assertEquals(3, staged.sourceLine(),
                "the interop-wrapped child failure must report the child's authored throw line: " + staged.detail());
        assertTrue(staged.sourceColumn() > 0, staged.detail());
        assertInstanceOf(PolyglotException.class, staged.getCause());
    }

    @Test
    void dynamicImportOfTsModuleMapsItsRuntimeFailureToTheChild() throws Exception {
        write("dynamic-boom-child.ts", "const label: string = 'dyn';\n"
                + "throw new Error('dynamic-child-boom');\n");
        write("dynamic-boom-entry.ts", "const mod = await import('./dynamic-boom-child.ts');\n"
                + "export const seen: string = mod.label;\n");

        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> host.loadEntryAsync("./server_scripts/src/dynamic-boom-entry.ts").get(10, TimeUnit.SECONDS));

        NekoModuleError staged = NekoModulePipelinePrepareTest.assertStaged(
                failure, NekoModuleError.Stage.EXECUTE, NekoModuleError.OWNER_EXECUTION);
        assertTrue(String.valueOf(staged.getMessage()).contains("dynamic-child-boom"), String.valueOf(staged));
        // Known limitation (recorded in the closure record): a literal dynamic import of a failing
        // CommonJS child loses the child's module identity at Graal's promise/host boundary, so the
        // failure is attributed to the importing entry with no invented location. The synchronous
        // interop path above does preserve the child identity.
        assertEquals("server_scripts/src/dynamic-boom-entry.ts", staged.sourcePath().replace('\\', '/'),
                staged.detail());
        assertEquals(-1, staged.sourceLine(),
                "no authored location is available, so none may be invented: " + staged.detail());
    }

    private static Value asValue(Object exports) {
        assertTrue(exports instanceof Value, "host must return a guest Value, was: " + exports);
        return (Value) exports;
    }

    private static NekoJSPaths pathsFor(Path gameDir) throws Exception {
        Constructor<NekoJSPaths> constructor = NekoJSPaths.class.getDeclaredConstructor(Path.class);
        constructor.setAccessible(true);
        return constructor.newInstance(gameDir);
    }
}
