package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.ScriptId;
import com.tkisor.nekojs.core.ScriptFilePolicy;
import com.tkisor.nekojs.core.compiler.NekoTypeScriptLanguagePlugin;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.DefaultErrorTracker;
import com.tkisor.nekojs.core.error.ScriptError;
import com.tkisor.nekojs.core.fs.NekoJSFileSystem;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.fs.NekoModuleReadService;
import com.tkisor.nekojs.core.fs.SandboxPolicy;
import com.tkisor.nekojs.testfixture.TestModulePreparation;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.HostAccess;
import graal.graalvm.polyglot.Source;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工单 11 AC7：跨 import 的执行错误必须经 source map 回到<strong>原始文件、行列和模块身份</strong>。
 *
 * <p>观测路径与生产一致：Graal 执行错误 → {@link ScriptError#create}（{@code DefaultErrorTracker}
 * 的错误解析入口，含 {@code SourceMapRegistry} 查表）→ 用户可见的
 * {@link ScriptError#getDisplayPath()} / 行 / 列。即：Execution Environment 不得吞掉
 * Preparation/Resolution 阶段写下的 source location。
 */
class NekoModuleErrorMappingCorpusTest {

    private NekoJSPaths paths;
    private Context context;
    private NekoScriptModuleLoaderHost host;
    private NekoModulePreparation preparation;
    private DefaultErrorTracker tracker;
    private final List<Path> written = new ArrayList<>();

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @BeforeEach
    void setUp() throws IOException {
        paths = NekoJSPaths.get();
        SandboxConfig config = SandboxConfig.defaultConfig();
        ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        compilers.register(NekoTypeScriptLanguagePlugin.INSTANCE);
        preparation = TestModulePreparation.create(paths, compilers, config);
        tracker = new DefaultErrorTracker(paths, config);
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
                new NekoModuleResolver(paths, new ScriptFilePolicy(compilers)), paths, preparation);
        context.getBindings("js").putMember("__nekoScriptModuleLoaderHost", host);
        try (var in = getClass().getResourceAsStream("/nekojs/node/internal/script-loader.js")) {
            assertNotNull(in, "script-loader.js must be on the test classpath");
            context.eval(Source.newBuilder("js", new String(in.readAllBytes(), StandardCharsets.UTF_8),
                    "nekojs/node/internal/script-loader.js").build());
        }
    }

    @AfterEach
    void tearDown() throws IOException {
        context.close();
        // 真删文件 + 空的父目录：deleteOnExit 只在 JVM 退出时生效，会污染共享 gameDir
        TestModulePreparation.deleteFilesAndEmptyParents(written);
        written.clear();
    }

    private Path write(String name, String source) throws IOException {
        Path file = paths.serverScripts().resolve("error-mapping/" + name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
        written.add(file);
        return file;
    }

    private ScriptError capture(String entry, String idNamespace) {
        Throwable failure = assertThrows(Throwable.class,
                () -> host.loadEntry("./server_scripts/error-mapping/" + entry));
        return ScriptError.create(ScriptType.SERVER, ScriptId.of("rt", idNamespace), entry, failure, tracker);
    }

    /**
     * 跨 import 的执行错误必须回到<strong>被导入模块</strong>的原始文件、行列与模块身份（AC7 字面三项）。
     * 同时断言错误归属的模块身份与 Resolution/Cache 对同一文件认定的身份一致。
     */
    @Test
    void executionErrorInImportedEsmMapsBackToImportedFile() throws IOException {
        Path helper = write("helper.mjs", """
                export function boom() {
                  throw new Error('kaboom');
                }
                """);
        write("entry.mjs", """
                import { boom } from './helper.mjs';
                boom();
                """);

        ScriptError error = capture("entry.mjs", "esm-import-error");
        String expected = paths.root().relativize(helper).toString().replace('\\', '/');
        assertEquals(expected, error.getDisplayPath(),
                "跨 import 的执行错误必须映射回被导入模块的原始文件（模块身份）");
        assertEquals(2, error.getLineNumber(), "必须回到 throw 所在的原始行");
        assertEquals(9, error.getColumnNumber(),
                "必须回到 throw 所在行的真实列（两空格缩进后 new 的 1-based 起始列）");
        assertTrue(error.getErrorMessage().contains("kaboom"), "错误消息必须保留：" + error.getErrorMessage());

        // 模块身份：错误归属的模块必须与 Resolution/Cache 认定的身份一致
        NekoPreparedModule helperModule = preparation.prepare(helper);
        assertEquals(helper.normalize().toAbsolutePath(), helperModule.sourcePath(),
                "错误归属的模块身份必须指向同一源路径");
        assertEquals("javascript", helperModule.languageId(), "被导入模块的语言身份必须可解释");
        assertNotNull(helperModule.cacheKey(), "被导入模块必须携带稳定 cache key（身份可追踪）");
    }

    /**
     * 本地 trusted 的 TS 源（经编译 + source map）执行错误必须回到<strong>原始 .ts</strong> 行列，
     * 而不是编译产物行号——这是 Graal execution 不吞掉准备阶段原始文件位置的证据。
     */
    @Test
    void executionErrorInCompiledLanguageMapsBackThroughSourceMap() throws IOException {
        Path helper = write("tshelper.ts", """
                export function boom(): void {
                  const x: number = 1;
                  throw new Error('tskaboom');
                }
                """);
        write("tsentry.mjs", """
                import { boom } from './tshelper.ts';
                boom();
                """);

        ScriptError error = capture("tsentry.mjs", "ts-source-map-error");
        String expected = paths.root().relativize(helper).toString().replace('\\', '/');
        assertEquals(expected, error.getDisplayPath(), "必须经 source map 回到原始 .ts 文件");
        assertEquals(3, error.getLineNumber(), "必须映射到原始 .ts 的 throw 行（不是编译产物行）");
        // TS 类型擦除的 source map 是语句粒度：列映射到语句起始列（此处 = 1）。
        // 断言「携带原始列」而不是编造不存在的精确列；粒度限制见 REPORT。
        assertTrue(error.getColumnNumber() > 0,
                "必须携带来自 source map 的原始列（TS 擦除为语句粒度）：" + error.getColumnNumber());
        assertTrue(error.getErrorMessage().contains("tskaboom"), "错误消息必须保留：" + error.getErrorMessage());

        // 模块身份：.ts helper 的语言与 module 模式必须可解释
        NekoPreparedModule helperModule = preparation.prepare(helper);
        assertEquals("typescript", helperModule.languageId(),
                "错误归属的 .ts 模块身份必须与语言注册表解析一致");
        assertEquals(com.tkisor.nekojs.core.compiler.NekoModuleMode.ESM, helperModule.mode(),
                "被导入 .ts 模块的 module 模式是模块身份的一部分");
    }

    /** 入口自身（非 import）的执行错误同样定位到原文件。 */
    @Test
    void executionErrorInEntryMapsBackToEntryFile() throws IOException {
        Path entry = write("self-entry.mjs", """
                const marker = 'm';
                throw new Error('entry-boom');
                """);

        ScriptError error = capture("self-entry.mjs", "entry-error");
        assertEquals(paths.root().relativize(entry).toString().replace('\\', '/'), error.getDisplayPath());
        assertEquals(2, error.getLineNumber(), "必须回到入口文件的原行");
        assertEquals(7, error.getColumnNumber(),
                "必须回到入口文件 throw 行的真实列（new 的 1-based 起始列）");
    }
}
