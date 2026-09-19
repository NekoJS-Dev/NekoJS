package com.tkisor.nekojs.core.module;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tkisor.nekojs.core.compiler.IScriptCompiler;
import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.NekoModuleMode;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.compiler.python.PythonToJsCompiler;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 票据 13 AC1 / AC3（准备侧）/ AC4：.py 经 {@link NekoModulePipelineCache#prepare} 得到
 * language id=python、正确的 module mode、可执行 JS、可用的 statement-level source map；
 * Python 源解析/转换错误在 PREPARE 阶段携带原始 .py 行列；源码 / 语言 / mode 变化使
 * prepared 身份失效。
 *
 * <p>断言口径是最高调用者 seam 的可观察结果（prepared module 字段、异常对象字段、
 * cache key、source-map 查询），不读私有 parser 对象引用。
 */
class NekoPythonPrepareTest {

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
        compilers.registerLanguage("python", Set.of(".py"), new PythonToJsCompiler());
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

    // ---- AC1: language id / mode / executable code / usable source map ----

    @Test
    void pythonDefinitionsPublishLanguageModeAndExecutableCode() throws Exception {
        String source = "# comment line\n"
                + "def greet(name):\n"
                + "    return 'hi ' + name\n"
                + "value = greet('neko')\n";
        NekoPreparedModule prepared = cache.prepare(write("py/prepare_defs.py", source));

        assertEquals("python", prepared.languageId());
        assertEquals(NekoModuleMode.ESM, prepared.mode(),
                "a module that defines top-level names is importable, so it must prepare as ESM");
        assertFalse(prepared.code().contains("def greet"), "source must be transpiled, not passed through");
        assertTrue(prepared.code().contains("function greet"), prepared.code());
        assertTrue(prepared.code().contains("module") || prepared.code().contains("export"),
                "the emitted code must be executable JS: " + prepared.code());
        assertEquals(0, prepared.prependedLineCount(),
                "the Python emitter prepends helper lines inside the map, not ahead of it");
        assertStatementMap(prepared, source);
    }

    /**
     * Prepended-line 语义：Python 会按需在模块顶部前置运行时助手与异常 prelude。这些行属于
     * 生成坐标，必须显式无映射（而不是被当成 authored 行），且不得让后续语句的映射整体偏移——
     * 这正是 {@code prependedLineCount=0} 的含义：前置行已经在 map 内部消化。
     */
    @Test
    void prependedHelperLinesAreUnmappedAndDoNotShiftAuthoredStatements() throws Exception {
        String withHelpers = "value = 7 // 2\n";
        NekoPreparedModule prepared = cache.prepare(write("py/prepend_helpers.py", withHelpers));
        assertEquals(0, prepared.prependedLineCount());
        assertTrue(prepared.code().indexOf("__nekoFloorDiv") < prepared.code().indexOf("var value"),
                "helpers are prepended ahead of the authored statements: " + prepared.code());

        SourceMapRegistry registry = new SourceMapRegistry(paths.root());
        registry.register(prepared.sourcePath(), prepared.sourceMap(), prepared.prependedLineCount());

        // 前置助手行不得映射成 authored 行 1。
        assertNull(mappedLineOrNull(registry, prepared, "__nekoFloorDiv"),
                "prepended helper lines must stay unmapped, not claim an authored line: " + prepared.code());
        // 作者写的语句仍映射到它自己的 Python 行。
        assertEquals(1, mappedLine(registry, prepared, "var value").line,
                "prepended helpers must not shift the authored statement line: " + prepared.code());

        // 对照：没有前置助手的 Python 模块，映射行号同样落在作者的 Python 行上。
        NekoPreparedModule plain = cache.prepare(write("py/prepend_plain.py", "value = 7\n"));
        assertFalse(plain.code().contains("__nekoFloorDiv"), plain.code());
        SourceMapRegistry plainRegistry = new SourceMapRegistry(paths.root());
        plainRegistry.register(plain.sourcePath(), plain.sourceMap(), plain.prependedLineCount());
        assertEquals(1, mappedLine(plainRegistry, plain, "var value").line);
    }

    @Test
    void pythonBareStatementsPrepareAsCommonJsAndStayExecutable() throws Exception {
        String source = "from nekojs import *\n"
                + "# the magic import is stripped, so this script defines no importable name\n"
                + "print('hello')\n";
        NekoPreparedModule prepared = cache.prepare(write("py/prepare_bare.py", source));

        assertEquals("python", prepared.languageId());
        assertEquals(NekoModuleMode.COMMONJS, prepared.mode(),
                "a bare script with no top-level definitions has nothing to export and stays CJS");
        assertTrue(prepared.code().contains("console.log("), prepared.code());
        assertFalse(prepared.code().contains("import "), "the magic nekojs import is stripped: " + prepared.code());
    }

    @Test
    void pythonSourceMapPublishesAuthoredPathAndContent() throws Exception {
        String source = "def f(a):\n    return a + 1\nresult = f(41)\n";
        NekoPreparedModule prepared = cache.prepare(write("py/prepare_map.py", source));

        JsonObject root = JsonParser.parseString(prepared.sourceMap()).getAsJsonObject();
        assertEquals(3, root.get("version").getAsInt());
        assertFalse(root.get("mappings").getAsString().isBlank(), "the map must carry mappings");
        assertEquals(source, root.getAsJsonArray("sourcesContent").get(0).getAsString(),
                "authored source must be retained verbatim for diagnostics");

        // 可观察行为：经 registry 查询必须回到 authored .py 路径、authored 行与 authored 内容。
        SourceMapRegistry registry = new SourceMapRegistry(paths.root());
        registry.register(prepared.sourcePath(), prepared.sourceMap(), prepared.prependedLineCount());
        SourceMapRegistry.OriginalPosition mapped =
                mappedLine(registry, prepared, "function f");
        assertEquals(prepared.sourcePath().replace('\\', '/'), mapped.path.replace('\\', '/'),
                "a mapped position must resolve back onto the authored .py file");
        assertEquals(1, mapped.line, "the def line must map onto its authored line");
        assertEquals(source, mapped.sourceContent,
                "the mapped position must carry the authored Python source content");
    }

    // ---- AC3 (prepare side): authored file/line/column for Python source errors ----

    @Test
    void pythonLexErrorCarriesAuthoredFileLineAndColumn() throws Exception {
        Path file = write("py/bad_lex.py", "value = 1\nother = 2\nbad = \u00a7\n");

        Exception failure = assertThrows(Exception.class, () -> cache.prepare(file));

        NekoModuleError staged = NekoModulePipelinePrepareTest.assertStaged(
                failure, NekoModuleError.Stage.PREPARE, NekoModuleError.OWNER_PREPARATION);
        assertTrue(staged.sourcePath().replace('\\', '/').endsWith("py/bad_lex.py"), staged.detail());
        assertEquals(3, staged.sourceLine(), staged.detail());
        assertEquals(7, staged.sourceColumn(),
                "the authored column of the offending character must be published: " + staged.detail());
        assertEquals("python", languageOf(staged), "the failure must not be reported as JS: " + staged.getMessage());
    }

    @Test
    void pythonParseErrorCarriesAuthoredFileLineAndColumn() throws Exception {
        Path file = write("py/bad_parse.py", "def ok():\n    return 1\nvalue = (1 + )\n");

        NekoModuleError staged = NekoModulePipelinePrepareTest.assertStaged(
                assertThrows(Exception.class, () -> cache.prepare(file)),
                NekoModuleError.Stage.PREPARE, NekoModuleError.OWNER_PREPARATION);

        assertTrue(staged.sourcePath().replace('\\', '/').endsWith("py/bad_parse.py"), staged.detail());
        assertEquals(3, staged.sourceLine(), staged.detail());
        assertTrue(staged.sourceColumn() > 0, staged.detail());
    }

    @Test
    void pythonEmitterErrorCarriesTheAuthoredStatementLine() throws Exception {
        // del <name> 无法在 JS 里解绑，是发射期错误（不是词法/语法错误）。
        Path file = write("py/bad_emit.py", "value = 1\nother = 2\ndel other\n");

        NekoModuleError staged = NekoModulePipelinePrepareTest.assertStaged(
                assertThrows(Exception.class, () -> cache.prepare(file)),
                NekoModuleError.Stage.PREPARE, NekoModuleError.OWNER_PREPARATION);

        assertTrue(staged.sourcePath().replace('\\', '/').endsWith("py/bad_emit.py"), staged.detail());
        assertEquals(3, staged.sourceLine(), staged.detail());
        assertTrue(staged.getMessage().contains("python source line 3"), staged.detail());
    }

    @Test
    void pythonIndentationErrorCarriesTheAuthoredLine() throws Exception {
        Path file = write("py/bad_indent.py", "def f():\n    return 1\n  return 2\n");

        NekoModuleError staged = NekoModulePipelinePrepareTest.assertStaged(
                assertThrows(Exception.class, () -> cache.prepare(file)),
                NekoModuleError.Stage.PREPARE, NekoModuleError.OWNER_PREPARATION);

        assertTrue(staged.sourcePath().replace('\\', '/').endsWith("py/bad_indent.py"), staged.detail());
        assertEquals(3, staged.sourceLine(), staged.detail());
    }

    // ---- AC4: cache identity invalidation ----

    @Test
    void unchangedPythonInputsHitTheSamePreparedIdentity() throws Exception {
        Path file = write("py/hit.py", "def f(a):\n    return a\nvalue = f(1)\n");
        NekoPreparedModule first = cache.prepare(file);
        NekoPreparedModule second = cache.prepare(file);

        assertEquals(first.cacheKey(), second.cacheKey(), "identical Python input must be a stable cache hit");
        assertEquals("python", second.languageId());
    }

    @Test
    void contentModeAndLanguageChangesInvalidatePythonEntries() throws Exception {
        Path file = write("py/stale.py", "def f():\n    return 'AAAA'\nvalue = f()\n");
        NekoPreparedModule before = cache.prepare(file);
        Files.writeString(file, "def f():\n    return 'BBBB'\nvalue = f()\n");
        NekoPreparedModule after = cache.prepare(file);
        assertTrue(after.code().contains("BBBB"), after.code());
        assertNotEquals(before.cacheKey(), after.cacheKey(), "Python content change must invalidate the entry");

        // mode: 同路径从「有顶层定义（ESM）」改成「纯语句脚本（CJS）」
        Path mode = write("py/mode.py", "def f():\n    return 1\nvalue = f()\n");
        NekoPreparedModule esm = cache.prepare(mode);
        assertEquals(NekoModuleMode.ESM, esm.mode());
        Files.writeString(mode, "from nekojs import *\nprint('bare')\n");
        NekoPreparedModule cjs = cache.prepare(mode);
        assertEquals(NekoModuleMode.COMMONJS, cjs.mode());
        assertNotEquals(esm.cacheKey(), cjs.cacheKey(), "Python mode change must invalidate the entry");

        // language identity: 同后缀换成另一个已注册语言
        Path lang = write("py/lang.py", "x = 1\ny = 2\n");
        NekoPreparedModule beforeLanguage = cache.prepare(lang);
        assertEquals("python", beforeLanguage.languageId());
        compilers.registerLanguage("python-replacement", Set.of(".py"), prefixingCompiler(".py", "// replacement\n"));
        NekoPreparedModule afterLanguage = cache.prepare(lang);
        assertEquals("python-replacement", afterLanguage.languageId());
        assertNotEquals(beforeLanguage.cacheKey(), afterLanguage.cacheKey(),
                "Python language identity change must invalidate the prepared entry");
    }

    // ---- helpers ----

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

    private static String languageOf(NekoModuleError staged) {
        String message = String.valueOf(staged.getMessage());
        int start = message.indexOf("[language=");
        if (start < 0) {
            return "";
        }
        int end = message.indexOf(',', start);
        return end < 0 ? message.substring(start + "[language=".length()) : message.substring(start + "[language=".length(), end);
    }

    /**
     * Source map must be usable: every generated line that carries an emitted Python statement maps
     * to that statement's authored line, and every other generated line (runtime helpers / exception
     * prelude / export block) is explicitly unmapped instead of inventing a generated line as
     * authored. Prelude lines are recorded as unmapped on purpose — see the migration material.
     */
    private void assertStatementMap(NekoPreparedModule prepared, String authoredSource) {
        assertNotNull(prepared.sourceMap(), "every prepared Python module must publish a source map");
        SourceMapRegistry registry = new SourceMapRegistry(paths.root());
        registry.register(prepared.sourcePath(), prepared.sourceMap(), prepared.prependedLineCount());

        int authoredLines = authoredSource.split("\\n", -1).length;
        int generatedLines = prepared.code().split("\\n", -1).length;
        int mapped = 0;
        for (int generatedLine = 1; generatedLine <= generatedLines; generatedLine++) {
            SourceMapRegistry.OriginalPosition position =
                    registry.getMappedPosition(prepared.sourcePath(), generatedLine, 1);
            if (position.path == null) {
                continue;   // helper/prelude/export line: honestly unmapped
            }
            mapped++;
            assertEquals(prepared.sourcePath().replace('\\', '/'), position.path.replace('\\', '/'));
            assertTrue(position.line >= 1 && position.line <= authoredLines,
                    "generated line " + generatedLine + " must map into authored lines, was " + position);
        }
        assertTrue(mapped > 0, "a Python module with statements must publish statement mappings");
        // 顶层 define 的生成行必须精确映射到它所在的 Python 行。
        assertEquals(2, mappedLine(registry, prepared, "function greet").line,
                "the emitted function must map to its authored def line");
    }

    /** Authored position for {@code needle}, or {@code null} when the line is deliberately unmapped. */
    private static SourceMapRegistry.OriginalPosition mappedLineOrNull(SourceMapRegistry registry,
                                                                       NekoPreparedModule prepared, String needle) {
        String[] generated = prepared.code().split("\n", -1);
        for (int i = 0; i < generated.length; i++) {
            if (!generated[i].contains(needle)) {
                continue;
            }
            SourceMapRegistry.OriginalPosition position =
                    registry.getMappedPosition(prepared.sourcePath(), i + 1, 1);
            return position.path == null ? null : position;
        }
        throw new AssertionError("generated code must contain a line with " + needle + ": " + prepared.code());
    }

    /** Authored position the generated line containing `needle` maps to. */
    private static SourceMapRegistry.OriginalPosition mappedLine(SourceMapRegistry registry,
                                                                  NekoPreparedModule prepared, String needle) {
        String[] generated = prepared.code().split("\\n", -1);
        for (int i = 0; i < generated.length; i++) {
            if (!generated[i].contains(needle)) {
                continue;
            }
            SourceMapRegistry.OriginalPosition position =
                    registry.getMappedPosition(prepared.sourcePath(), i + 1, 1);
            if (position.path != null) {
                return position;
            }
        }
        throw new AssertionError("generated code must contain a mapped line with " + needle + ": " + prepared.code());
    }

    private static NekoJSPaths pathsFor(Path gameDir) throws Exception {
        Constructor<NekoJSPaths> constructor = NekoJSPaths.class.getDeclaredConstructor(Path.class);
        constructor.setAccessible(true);
        return constructor.newInstance(gameDir);
    }
}