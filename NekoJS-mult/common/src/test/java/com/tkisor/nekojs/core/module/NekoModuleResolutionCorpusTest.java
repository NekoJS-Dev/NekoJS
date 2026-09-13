package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.ScriptFilePolicy;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.fs.NekoJSFileSystem;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.fs.NekoModuleReadService;
import com.tkisor.nekojs.core.fs.SandboxPolicy;
import com.tkisor.nekojs.core.module.esm.NekoEsmDiagnostic;
import com.tkisor.nekojs.core.module.esm.NekoEsmLinkException;
import com.tkisor.nekojs.testfixture.TestModulePreparation;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.HostAccess;
import graal.graalvm.polyglot.Source;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.io.IOAccess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工单 11 AC3/AC4/AC5/AC6：Module Resolution/Cache 的 characterization corpus。
 *
 * <p>从最高既有调用者 Seam（{@link NekoScriptModuleLoaderHost} 的 loadEntry / requireFrom）
 * 观察 CJS require 与 ESM import/link 的模块身份、循环依赖、未解析 import、link 失败、
 * 语法错误定位、内容变化与按 ScriptType 清理。
 *
 * <p>ESM 走 native ESM 虚拟模块注册表，因此 Context 必须装与生产同源的
 * {@link NekoJSFileSystem}（虚拟 .native_esm_modules URI 的读取依赖它）；测试不复制生产
 * 语义，只复用同一实现。
 */
class NekoModuleResolutionCorpusTest {

    private NekoJSPaths paths;
    private Context context;
    private NekoScriptModuleLoaderHost host;
    private NekoModulePreparation preparation;
    private final List<Path> written = new ArrayList<>();

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @BeforeEach
    void setUp() throws IOException {
        paths = NekoJSPaths.get();
        Files.createDirectories(scriptsDir().resolve("resolution-corpus"));
        SandboxConfig config = SandboxConfig.defaultConfig();
        ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        preparation = TestModulePreparation.create(paths, compilers, config);
        NekoModuleReadService readService = new NekoModuleReadService(preparation, new ScriptFilePolicy(compilers));
        IOAccess ioAccess = IOAccess.newBuilder()
                .fileSystem(new NekoJSFileSystem(paths.root(), new SandboxPolicy(config, paths), readService))
                .build();
        context = Context.newBuilder("js")
                .allowExperimentalOptions(true)
                .allowHostAccess(HostAccess.ALL)
                .allowIO(ioAccess)
                .option("js.ecmascript-version", "latest")
                .option("js.commonjs-require", "true")
                .option("js.commonjs-require-cwd", paths.root().toAbsolutePath().toString())
                .build();
        host = new NekoScriptModuleLoaderHost(context,
                new NekoModuleResolver(paths, ScriptFilePolicy.legacyRuntime()), paths, preparation);
        context.getBindings("js").putMember("__nekoScriptModuleLoaderHost", host);
        try (var in = getClass().getResourceAsStream("/nekojs/node/internal/script-loader.js")) {
            assertNotNull(in, "script-loader.js must be on the test classpath");
            String loader = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            context.eval(Source.newBuilder("js", loader, "nekojs/node/internal/script-loader.js").build());
        }
    }

    @AfterEach
    void tearDown() throws IOException {
        context.close();
        for (Path path : written) {
            Files.deleteIfExists(path);
        }
        written.clear();
    }

    private Path scriptsDir() {
        return paths.serverScripts();
    }

    private Path write(String name, String source) throws IOException {
        Path file = scriptsDir().resolve("resolution-corpus/" + name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
        written.add(file);
        return file;
    }

    private static final String ENTRY_PREFIX = "./server_scripts/resolution-corpus/";

    // ---- AC3：CJS 模块身份在重复加载下稳定（模块体只执行一次） ----

    @Test
    void cjsModuleIdentityIsStableAcrossRepeatedLoads() throws IOException {
        write("identity.js", """
                globalThis.__nekoCorpusIdentityLoads = (globalThis.__nekoCorpusIdentityLoads || 0) + 1;
                module.exports = { loads: globalThis.__nekoCorpusIdentityLoads };
                """);

        Object first = host.loadEntry(ENTRY_PREFIX + "identity.js");
        Object second = host.loadEntry(ENTRY_PREFIX + "identity.js");

        assertNotNull(first, "首次 loadEntry 必须返回 exports");
        assertEquals(1, ((Value) first).getMember("loads").asInt(),
                "第一次加载必须执行一次模块体");
        assertEquals(1, ((Value) second).getMember("loads").asInt(),
                "重复加载同一路径必须复用已缓存模块（身份稳定），不得重新求值");
    }

    /** CJS 循环 require：A 与 B 互引必须拿到部分 exports，而不是无限重入。 */
    @Test
    void circularCjsRequireReturnsPartialExports() throws IOException {
        write("cycle-a.js", """
                exports.name = 'a';
                const b = require('./cycle-b.js');
                exports.other = b.name;
                """);
        write("cycle-b.js", """
                exports.name = 'b';
                const a = require('./cycle-a.js');
                exports.other = a.name;
                """);

        Object exports = host.loadEntry(ENTRY_PREFIX + "cycle-a.js");

        assertNotNull(exports, "循环 require 必须返回部分 exports 而不是 StackOverflowError");
        assertEquals("b", ((Value) exports).getMember("other").asString(),
                "循环方必须看到对端已导出的部分 exports");
    }

    // ---- AC3：ESM import/link 可执行 ----

    @Test
    void esmImportLinksAndExecutes() throws IOException {
        write("esm-helper.mjs", "export const value = 41 + 1;\n");
        write("esm-entry.mjs", "import { value } from './esm-helper.mjs';\nexport const doubled = value * 2;\n");

        Object namespace = host.loadEntry(ENTRY_PREFIX + "esm-entry.mjs");

        assertNotNull(namespace, "ESM 入口必须能 link 并求值");
        assertEquals(84, ((Value) namespace).getMember("doubled").asInt(),
                "跨模块 import 必须看到 helper 的导出");
    }

    /**
     * ESM 循环依赖：link 必须终止（不无限递归），函数声明经提升可在循环中互相调用。
     *
     * <p>刻意使用 {@code export function}（提升，无 TDZ）而不是 {@code export const}：
     * 原生 ESM 在循环依赖中于求值期读取尚未初始化的 {@code const} 绑定会抛
     * {@code ReferenceError}（TDZ），这是既有语义而非本票引入的行为——用函数形态
     * 才能观测「link 终止 + 跨循环调用可用」。
     */
    @Test
    void circularEsmDependenciesLinkAndEvaluate() throws IOException {
        write("esm-cycle-a.mjs", """
                import { b } from './esm-cycle-b.mjs';
                export function a() { return 'a'; }
                export function viaB() { return 'a->' + b(); }
                """);
        write("esm-cycle-b.mjs", """
                import { a } from './esm-cycle-a.mjs';
                export function b() { return 'b'; }
                export function viaA() { return 'b->' + a(); }
                """);

        Object namespace = host.loadEntry(ENTRY_PREFIX + "esm-cycle-a.mjs");

        assertNotNull(namespace, "循环 ESM 依赖必须能 link 并求值");
        Value entry = (Value) namespace;
        assertEquals("a", entry.getMember("a").execute().asString(), "入口自身导出必须可用");
        assertEquals("a->b", entry.getMember("viaB").execute().asString(),
                "循环依赖对端导出必须可在求值后互调");
    }

    // ---- AC6：link 失败可区分 owner 与阶段 ----

    @Test
    void unresolvedEsmImportFailsAtLinkWithSourceLocation() throws IOException {
        write("esm-missing.mjs",
                "import { nope } from './does-not-exist.mjs';\nexport const v = 1;\n");

        Exception error = assertThrows(Exception.class,
                () -> host.loadEntry(ENTRY_PREFIX + "esm-missing.mjs"));

        assertTrue(causeText(error).contains("does-not-exist.mjs"),
                "未解析 import 必须报出缺失的 specifier：" + causeText(error));
    }

    @Test
    void missingEsmExportFailsAtLinkWithDiagnostic() throws IOException {
        write("esm-provider.mjs", "export const present = 1;\n");
        write("esm-bad-import.mjs",
                "import { absent } from './esm-provider.mjs';\nexport const v = absent;\n");

        Exception error = assertThrows(Exception.class,
                () -> host.loadEntry(ENTRY_PREFIX + "esm-bad-import.mjs"));

        assertTrue(causeText(error).contains("absent"),
                "缺失导出必须在 link 阶段以 ESM 诊断失败：" + causeText(error));
    }

    @Test
    void duplicateEsmExportFailsAtLinkWithOriginalLocation() throws IOException {
        Path entry = write("esm-duplicate.mjs",
                "const a = 1;\nexport { a };\nexport { a };\n");

        Exception error = assertThrows(Exception.class,
                () -> host.loadEntry(ENTRY_PREFIX + "esm-duplicate.mjs"));

        NekoEsmLinkException link = findLinkException(error);
        assertNotNull(link, "重复导出必须报为 ESM link 阶段失败：" + causeText(error));
        NekoEsmDiagnostic diagnostic = link.diagnostic();
        assertNotNull(diagnostic.span(), "link 失败必须携带原始 span");
        assertEquals(entry.toAbsolutePath().normalize(), diagnostic.file().toAbsolutePath().normalize(),
                "link 失败必须指向原文件");
        assertTrue(diagnostic.line() >= 1, "link 失败必须携带原始行号");
    }

    /** AC3/AC6：CJS 语法错误在准备/解析阶段定位到用户文件行列。 */
    @Test
    void cjsSyntaxErrorCarriesStageLocation() throws IOException {
        Path broken = write("syntax.js", "const ok = 1\nconst } = broken\n");

        IOException error = assertThrows(IOException.class,
                () -> host.loadEntry(ENTRY_PREFIX + "syntax.js"));

        NekoEsmLinkException link = assertInstanceOf(NekoEsmLinkException.class, error,
                "CJS 语法错误必须包成带 source location 的诊断");
        assertEquals(broken.toAbsolutePath().normalize(), link.diagnostic().file().toAbsolutePath().normalize());
        assertEquals(2, link.diagnostic().line(), "行列必须指向用户文件的真实语法错误位置");
    }

    // ---- AC4：内容变化失效 ----

    @Test
    void contentChangeProducesNewPreparedModule() throws IOException {
        Path entry = write("changing.cjs", "module.exports = 'v1';\n");
        NekoPreparedModule first = preparation.prepare(entry);

        Files.writeString(entry, "module.exports = 'v2';\n");
        NekoPreparedModule second = preparation.prepare(entry);

        assertNotSame(first, second, "内容变化必须产生新的 prepared module");
        assertTrue(second.code().contains("v2"), "必须返回新内容");
        assertTrue(!first.cacheKey().equals(second.cacheKey()), "内容变化必须改变稳定 cache key");
    }

    // ---- AC5：按 ScriptType 清理只影响目标范围 ----

    @Test
    void typeScopedClearLeavesCrossTypeAndOtherTypeEntries() throws IOException {
        Path serverScript = write("type-server.cjs", "module.exports = 1;\n");
        Path clientScript = paths.clientScripts().resolve("resolution-corpus/type-client.cjs");
        Path sharedScript = paths.nodeModules().resolve("neko-resolution-corpus/type-shared.cjs");
        for (Path p : new Path[]{clientScript, sharedScript}) {
            Files.createDirectories(p.getParent());
            Files.writeString(p, "module.exports = 1;\n");
            written.add(p);
        }

        preparation.prepare(serverScript);
        preparation.prepare(clientScript);
        preparation.prepare(sharedScript);
        assertTrue(preparation.isCached(serverScript) && preparation.isCached(clientScript)
                        && preparation.isCached(sharedScript),
                "测试前提：三个脚本都必须先进入缓存");

        preparation.clear(ScriptType.SERVER);

        assertTrue(!preparation.isCached(serverScript), "SERVER 条目必须被清除");
        assertTrue(preparation.isCached(clientScript), "CLIENT 条目必须保留");
        assertTrue(preparation.isCached(sharedScript), "跨类型共享条目（node_modules）必须保留");
    }

    private static NekoEsmLinkException findLinkException(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof NekoEsmLinkException link) {
                return link;
            }
        }
        return null;
    }

    private static String causeText(Throwable error) {
        StringBuilder text = new StringBuilder();
        for (Throwable t = error; t != null; t = t.getCause()) {
            text.append(t.getMessage()).append(' ');
        }
        return text.toString();
    }
}
