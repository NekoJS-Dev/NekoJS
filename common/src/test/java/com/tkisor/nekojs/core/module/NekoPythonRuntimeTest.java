package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.ScriptFilePolicy;
import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.NekoModuleMode;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.compiler.python.PythonToJsCompiler;
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
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the Python transpiler output through the shared Resolution/Cache and a real Graal
 * execution. Existing Python semantics (indentation, definitions/classes/calls, comments, module
 * imports) are pinned by execution results; runtime exceptions map back through the source map to
 * their authored .py line and column while keeping the module identity; error stages and module
 * ownership stay observable when Python and JS/ESM load together; a changed dependency invalidates
 * the cache.
 *
 * <p>Observations are made at the top-level caller: the value returned by
 * {@code loadEntry}/{@code loadEntryAsync}, the exception object fields
 * (stage/owner/sourcePath/sourceLine/sourceColumn/moduleId), the guest cause and the shared
 * prepared cache. Private parser object identity is not inspected.
 */
class NekoPythonRuntimeTest {

    @TempDir
    Path gameDir;

    private NekoJSPaths paths;
    private ScriptCompilerRegistry compilers;
    private NekoModulePipelineCache cache;
    private Context context;
    private NekoScriptModuleLoaderHost host;

    @BeforeEach
    void setUp() throws Exception {
        TestPlatformInit.ensureInitialized(gameDir);
        paths = pathsFor(gameDir);
        Files.createDirectories(paths.serverScripts().resolve("src"));
        compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        compilers.registerLanguage("python", Set.of(".py"), new PythonToJsCompiler());
        SandboxConfig config = SandboxConfig.defaultConfig();
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
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
        return file;
    }

    // ---- Existing Python semantics run through the shared pipeline ----

    @Test
    void indentationDefinitionsCallsAndCommentsRunUnchanged() throws Exception {
        write("semantics.py", "# leading comment stays inert\n"
                + "def classify(n):\n"
                + "    if n < 0:\n"
                + "        return 'neg'\n"
                + "    elif n == 0:\n"
                + "        return 'zero'\n"
                + "    else:\n"
                + "        # a nested comment must not shift the generated lines\n"
                + "        return 'pos'\n"
                + "total = 0\n"
                + "for n in range(-1, 2):\n"
                + "    total += 1\n"
                + "combined = classify(0) + classify(1) + classify(-1) + str(total)\n");

        Value exports = asValue(host.loadEntry("./server_scripts/src/semantics.py"));

        assertEquals("zeroposneg3", exports.getMember("combined").asString(),
                "indentation, else/elif, comments and for-loop semantics must survive transpilation");
    }

    @Test
    void classesAndClosuresRunUnchanged() throws Exception {
        write("classes.py", "class Counter:\n"
                + "    def __init__(self, start=0):\n"
                + "        self.value = start\n"
                + "    def bump(self, by):\n"
                + "        self.value += by\n"
                + "        return self.value\n"
                + "    def label(self):\n"
                + "        return f'n={self.value}'\n"
                + "\n"
                + "def make():\n"
                + "    counter = Counter(10)\n"
                + "    def step():\n"
                + "        return counter.bump(5)\n"
                + "    return step\n"
                + "step = make()\n"
                + "first = step()\n"
                + "second = step()\n"
                + "text = Counter(1).label()\n");

        Value exports = asValue(host.loadEntry("./server_scripts/src/classes.py"));

        assertEquals(15, exports.getMember("first").asInt(), "class method + augmented assignment");
        assertEquals(20, exports.getMember("second").asInt(), "closure must keep mutating the same instance");
        assertEquals("n=1", exports.getMember("text").asString(), "default parameter + f-string");
    }

    @Test
    void pythonModulesImportEachOtherThroughTheSharedResolver() throws Exception {
        write("mathlib.py", "def double(n):\n"
                + "    return n * 2\n"
                + "\n"
                + "TAG = 'mathlib'\n");
        write("uses_math.py", "from mathlib import double, TAG\n"
                + "import mathlib\n"
                + "value = double(21)\n"
                + "tag = TAG\n"
                + "same = mathlib.double is double\n");

        Value exports = asValue(host.loadEntry("./server_scripts/src/uses_math.py"));

        assertEquals(42, exports.getMember("value").asInt(), "named import from a sibling .py module");
        assertEquals("mathlib", exports.getMember("tag").asString());
        assertTrue(exports.getMember("same").asBoolean(),
                "named import and namespace import must expose the same module identity");
    }

    // ---- Runtime failures map back to authored .py positions ----

    @Test
    void runtimeFailureInRaisedFunctionReportsAuthoredLineAndIdentity() throws Exception {
        write("boom.py", "def helper(n):\n"
                + "    total = n + 1\n"
                + "    if total > 0:\n"
                + "        raise ValueError('py-boom')\n"
                + "    return total\n"
                + "\n"
                + "helper(1)\n");

        IOException failure = assertThrows(IOException.class,
                () -> host.loadEntry("./server_scripts/src/boom.py"));

        NekoModuleError staged = NekoModulePipelinePrepareTest.assertStaged(
                failure, NekoModuleError.Stage.EXECUTE, NekoModuleError.OWNER_EXECUTION);
        assertTrue(String.valueOf(staged.getMessage()).contains("py-boom"), String.valueOf(staged));
        assertEquals("server_scripts/src/boom.py", staged.sourcePath().replace('\\', '/'));
        assertEquals("server_scripts/src/boom.py", staged.moduleId().replace('\\', '/'),
                "the failing module identity must stay the authored .py module");
        assertEquals(4, staged.sourceLine(),
                "the raise must be reported at its authored Python line, not at a generated prelude line: "
                        + staged.detail());
        assertTrue(staged.sourceColumn() > 0, staged.detail());
        assertInstanceOf(PolyglotException.class, staged.getCause(),
                "the guest cause must remain available for diagnostics");
    }

    @Test
    void runtimeFailureInCommonJsShapedPythonReportsAuthoredLineNotGeneratedLine() throws Exception {
        // A script without a top-level define or assignment is CJS-shaped, so its output carries prepended
        // helper lines such as __nekoDiv.
        write("cjs-mode.py", "from nekojs import *\n"
                + "print('start')\n"
                + "print(10 / 0)\n");

        IOException failure = assertThrows(IOException.class,
                () -> host.loadEntry("./server_scripts/src/cjs-mode.py"));

        NekoModuleError staged = NekoModulePipelinePrepareTest.assertStaged(
                failure, NekoModuleError.Stage.EXECUTE, NekoModuleError.OWNER_EXECUTION);
        assertEquals("server_scripts/src/cjs-mode.py", staged.sourcePath().replace('\\', '/'));
        assertEquals(3, staged.sourceLine(),
                "generated helper lines must not be reported as the authored Python line: " + staged.detail());
        assertTrue(staged.sourceColumn() > 0, staged.detail());
    }

    /** Python source parse errors report the authored position during preparation, like lexer and
     * emit-stage errors. */
    @Test
    void pythonSourceParseErrorIsReportedAtPrepareWithTheAuthoredLine() throws Exception {
        // An unclosed call fails only at end of file in Python (NEWLINE lands on line 4), so the position is
        // the line and column the parser actually reports, not the line the call starts on.
        Path file = write("bad_parse_runtime.py", "def f():\n    return 1\nvalue = f(\n");

        Exception failure = assertThrows(Exception.class, () -> cache.prepare(file));

        NekoModuleError staged = NekoModulePipelinePrepareTest.assertStaged(
                failure, NekoModuleError.Stage.PREPARE, NekoModuleError.OWNER_PREPARATION);
        assertEquals("server_scripts/src/bad_parse_runtime.py", staged.sourcePath().replace('\\', '/'));
        assertEquals(4, staged.sourceLine(), staged.detail());
        assertTrue(staged.sourceColumn() > 0, staged.detail());
    }

    /**
     * A link failure of the generated JS. Python's {@code from <sibling> import <name>} transpiles into
     * an ESM static import; if the imported module does not export that name, the failure happens in JS
     * during linking, not in Python source parsing:
     *
     * <ul>
     *   <li>{@code cache.prepare} must succeed, because the Python source and the transpile are valid;</li>
     *   <li>{@code loadEntry} throws {@code Stage.LINK} / {@code Module Resolution/Cache}, whose cause
     *       is {@link NekoEsmLinkException}, the real JS link diagnostic;</li>
     *   <li>the failure stays distinguishable from {@code EXECUTE} (runtime exception) and
     *       {@code PREPARE} (Python source error).</li>
     * </ul>
     *
     * <p>The stage assertion fails if link failures are mislabeled as {@code EXECUTE}, for example when
     * the host swallows {@code NekoEsmLinkException}.
     */
    @Test
    void generatedJsLinkFailureIsDistinguishedFromPythonSourceAndExecutionErrors() throws Exception {
        write("link_sibling.py", "def present():\n"
                + "    return 1\n"
                + "\n"
                + "OTHER = 2\n");
        Path entry = write("link_entry.py", "from link_sibling import absent_name\n"
                + "value = 1\n");

        // Preparation cannot see this: the source is valid and transpiles to
        // `import { absent_name } from './link_sibling'`.
        NekoPreparedModule prepared = cache.prepare(entry);
        assertEquals("python", prepared.languageId());
        assertTrue(prepared.code().contains("import { absent_name }"), prepared.code());
        assertEquals(NekoModuleMode.ESM, prepared.mode());

        IOException failure = assertThrows(IOException.class,
                () -> host.loadEntry("./server_scripts/src/link_entry.py"));

        NekoModuleError staged = NekoModulePipelinePrepareTest.assertStaged(
                failure, NekoModuleError.Stage.LINK, NekoModuleError.OWNER_RESOLUTION_CACHE);
        assertTrue(String.valueOf(staged.getMessage()).contains("absent_name"), String.valueOf(staged));
        assertEquals("server_scripts/src/link_entry.py", staged.sourcePath().replace('\\', '/'),
                "the link failure must name the authored importing .py module: " + staged.detail());
        assertInstanceOf(NekoEsmLinkException.class, staged.getCause(),
                "the diagnostic must be the real generated-JS link failure, not a Python source error");

        // Distinguishing it from EXECUTE: the same sibling that exists but throws at runtime takes the other
        // path (EXECUTE).
        write("link_sibling.py", "def present():\n"
                + "    raise ValueError('sibling-runtime-boom')\n");
        write("link_entry.py", "from link_sibling import present\n"
                + "value = present()\n");
        IOException runtimeFailure = assertThrows(IOException.class,
                () -> host.loadEntry("./server_scripts/src/link_entry.py"));
        NekoModuleError runtimeStaged = NekoModulePipelinePrepareTest.assertStaged(
                runtimeFailure, NekoModuleError.Stage.EXECUTE, NekoModuleError.OWNER_EXECUTION);
        assertEquals("server_scripts/src/link_sibling.py", runtimeStaged.sourcePath().replace('\\', '/'),
                "a runtime failure in the sibling must be attributed to the sibling");
    }

    @Test
    void crossImportRuntimeFailureKeepsTheFailingChildPythonIdentity() throws Exception {
        write("child.py", "def explode():\n"
                + "    raise ValueError('child-boom')\n");
        write("outer.py", "from child import explode\n"
                + "explode()\n");

        IOException failure = assertThrows(IOException.class,
                () -> host.loadEntry("./server_scripts/src/outer.py"));

        NekoModuleError staged = NekoModulePipelinePrepareTest.assertStaged(
                failure, NekoModuleError.Stage.EXECUTE, NekoModuleError.OWNER_EXECUTION);
        assertTrue(String.valueOf(staged.getMessage()).contains("child-boom"), String.valueOf(staged));
        assertEquals("server_scripts/src/child.py", staged.sourcePath().replace('\\', '/'),
                "a nested Python failure must name the failing child, not the importing entry");
        assertEquals("server_scripts/src/child.py", staged.moduleId().replace('\\', '/'));
        assertEquals(2, staged.sourceLine(),
                "the child's authored raise line must survive the cross-import boundary: " + staged.detail());
    }

    @Test
    void asyncEntryLoadingOfPythonChildKeepsTheChildIdentityAndAuthoredLine() throws Exception {
        write("dyn_child.py", "def explode():\n"
                + "    raise ValueError('dyn-child-boom')\n"
                + "\n"
                + "explode()\n");
        write("dyn_outer.py", "import dyn_child\n"
                + "\n"
                + "value = 1\n");

        // loadEntryAsync must attribute failures like the sync entry: the failing .py child keeps its identity
        // and authored line.
        Throwable failure;
        try {
            failure = assertThrows(ExecutionException.class,
                    () -> host.loadEntryAsync("./server_scripts/src/dyn_outer.py").get(10, TimeUnit.SECONDS));
        } catch (AssertionError notAFuture) {
            // When the host throws the stage error synchronously before returning a future, observe that error
            // directly.
            failure = assertThrows(Exception.class,
                    () -> host.loadEntryAsync("./server_scripts/src/dyn_outer.py"));
        }

        NekoModuleError staged = NekoModulePipelinePrepareTest.assertStaged(
                failure, NekoModuleError.Stage.EXECUTE, NekoModuleError.OWNER_EXECUTION);
        assertTrue(String.valueOf(staged.getMessage()).contains("dyn-child-boom"), String.valueOf(staged));
        assertEquals("server_scripts/src/dyn_child.py", staged.sourcePath().replace('\\', '/'), staged.detail());
        assertEquals(4, staged.sourceLine(),
                "the async child's authored raise line must be reported: " + staged.detail());
    }

    // ---- The same resolution/cache invalidates Python modules ----

    @Test
    void changedPythonDependencyIsReloadedThroughTheSharedCache() throws Exception {
        write("dep_lib.py", "def tag():\n"
                + "    return 'one'\n");
        write("dep_entry.py", "from dep_lib import tag\n"
                + "value = tag()\n");

        Value before = asValue(host.loadEntry("./server_scripts/src/dep_entry.py"));
        assertEquals("one", before.getMember("value").asString());

        Files.writeString(paths.serverScripts().resolve("src/dep_lib.py"),
                "def tag():\n    return 'two'\n");
        host.invalidateModuleTree("./server_scripts/src/dep_entry.py");

        Value after = asValue(host.loadEntry("./server_scripts/src/dep_entry.py"));
        assertEquals("two", after.getMember("value").asString(),
                "a changed Python dependency must invalidate through the shared cache, not a second resolver");
    }

    /**
     * Dependency-driven invalidation: nothing calls {@code invalidateModuleTree} or {@code invalidate},
     * only the imported .py changes before the entry runs again. The host's
     * {@code refreshPreparedExecutionTree} must notice the child module's prepared key change and
     * invalidate it before the entry is served from the cache.
     *
     * <p>Complements {@code changedPythonDependencyIsReloadedThroughTheSharedCache}, which calls
     * {@code invalidateModuleTree} explicitly: that test proves explicit invalidation, this one
     * proves invalidation driven by the dependency.
     */
    @Test
    void changedPythonDependencyIsObservedWithoutAnExplicitInvalidate() throws Exception {
        write("auto_lib.py", "def tag():\n"
                + "    return 'auto-one'\n");
        write("auto_entry.py", "from auto_lib import tag\n"
                + "value = tag()\n");

        Value before = asValue(host.loadEntry("./server_scripts/src/auto_entry.py"));
        assertEquals("auto-one", before.getMember("value").asString());

        // Only the file on disk changes; no explicit invalidate or clear.
        Files.writeString(paths.serverScripts().resolve("src/auto_lib.py"),
                "def tag():\n    return 'auto-two'\n");

        Value after = asValue(host.loadEntry("./server_scripts/src/auto_entry.py"));
        assertEquals("auto-two", after.getMember("value").asString(),
                "a changed dependency must be observed by the automatic dependency-driven invalidation");
    }

    @Test
    void unchangedPythonDependencyIsNotReevaluated() throws Exception {
        write("once_lib.py", "calls = {'n': 0}\n"
                + "\n"
                + "def bump():\n"
                + "    calls['n'] = calls['n'] + 1\n"
                + "    return calls['n']\n");
        write("once_entry.py", "from once_lib import bump\n"
                + "first = bump()\n"
                + "second = bump()\n");

        Value exports = asValue(host.loadEntry("./server_scripts/src/once_entry.py"));

        assertEquals(1, exports.getMember("first").asInt(), "module state must be shared across imports");
        assertEquals(2, exports.getMember("second").asInt(),
                "the imported Python module must be evaluated once, like any other module identity");
    }

    // ---- Mixed Python, JS and ESM error attribution ----

    @Test
    void pythonAndEsmModulesImportEachOtherWithObservableAttribution() throws Exception {
        write("mixed_esm.mjs", "export const tag = 'esm';\n");
        write("mixed_py.py", "import mixed_esm\n"
                + "\n"
                + "def read():\n"
                + "    return mixed_esm.tag\n"
                + "\n"
                + "seen = read()\n");

        Value fromPython = asValue(host.loadEntry("./server_scripts/src/mixed_py.py"));
        assertEquals("esm", fromPython.getMember("seen").asString(),
                "a .py module must import a .mjs module through the shared resolution path");

        write("mixed_js_entry.mjs", "import { seen } from './mixed_py.py';\n"
                + "export const value = seen;\n");
        Value fromEsm = asValue(host.loadEntry("./server_scripts/src/mixed_js_entry.mjs"));
        assertEquals("esm", fromEsm.getMember("value").asString(),
                "an .mjs module must import a .py module through the same identity model");
    }

    @Test
    void missingPythonModuleIsResolvedAtTheResolveStageNotExecution() throws Exception {
        write("missing_import.py", "from not_there import thing\n"
                + "value = 1\n");

        IOException failure = assertThrows(IOException.class,
                () -> host.loadEntry("./server_scripts/src/missing_import.py"));

        NekoModuleError staged = NekoModulePipelinePrepareTest.assertStaged(
                failure, NekoModuleError.Stage.RESOLVE, NekoModuleError.OWNER_RESOLUTION_CACHE);
        assertTrue(String.valueOf(staged.getMessage()).contains("not_there"), String.valueOf(staged));
        assertEquals("server_scripts/src/missing_import.py", staged.sourcePath().replace('\\', '/'),
                "the RESOLVE failure must name the authored importing .py module: " + staged.detail());
        assertEquals("./not_there", staged.moduleId());
    }

    @Test
    void failingEsmChildImportedFromPythonKeepsTheEsmChildIdentity() throws Exception {
        write("esm_child.mjs", "export const ok = 1;\n"
                + "throw new Error('esm-child-boom');\n");
        write("esm_outer.py", "import esm_child\n"
                + "\n"
                + "value = esm_child.ok\n");

        IOException failure = assertThrows(IOException.class,
                () -> host.loadEntry("./server_scripts/src/esm_outer.py"));

        NekoModuleError staged = NekoModulePipelinePrepareTest.assertStaged(
                failure, NekoModuleError.Stage.EXECUTE, NekoModuleError.OWNER_EXECUTION);
        assertTrue(String.valueOf(staged.getMessage()).contains("esm-child-boom"), String.valueOf(staged));
        assertEquals("server_scripts/src/esm_child.mjs", staged.sourcePath().replace('\\', '/'),
                "a failing ESM child must keep its own identity instead of being attributed to the .py importer");
    }

    // ---- helpers ----

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