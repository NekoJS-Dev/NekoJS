package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.ScriptFilePolicy;
import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.NekoJsxLanguagePlugin;
import com.tkisor.nekojs.core.compiler.NekoTsxLanguagePlugin;
import com.tkisor.nekojs.core.compiler.NekoTypeScriptLanguagePlugin;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.compiler.python.PythonToJsCompiler;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.DefaultErrorTracker;
import com.tkisor.nekojs.core.error.DiagnosticPhase;
import com.tkisor.nekojs.core.error.ScriptDiagnosticRecord;
import com.tkisor.nekojs.core.error.ScriptError;
import com.tkisor.nekojs.core.error.SourceMapRegistry;
import com.tkisor.nekojs.core.fs.NekoJSFileSystem;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.fs.SandboxPolicy;
import com.tkisor.nekojs.core.module.NekoEsmVirtualModuleRegistry;
import com.tkisor.nekojs.core.node.NekoNodeModuleInstaller;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.io.IOAccess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ticket 30 language × phase matrix (JS/CJS/ESM/TS/JSX/TSX/Python × prepare / resolve-link /
 * cache / execution): representative failures recorded through the real module pipeline keep
 * phase, owner, authored source location, module identity and the prepared cache revision in
 * the frozen diagnostic record. Observed at the highest caller seams —
 * {@code NekoModulePipelineCache.prepare}, {@code loadEntry} and the error tracker's public
 * error set.
 */
class DiagnosticPhaseMatrixTest {

    /**
     * Shared stable game dir (not {@code @TempDir}): recording diagnostics writes the per-type
     * log file, whose appender stays open for the JVM lifetime on Windows and would block a
     * temp-dir cleanup. Same pattern as {@code Ticket06RunawayProbeTest}.
     */
    private static final Path GAME_DIR = Path.of(System.getProperty("java.io.tmpdir"),
            "nekojs-diag-matrix-gamedir");

    private NekoJSPaths paths;
    private ScriptCompilerRegistry compilers;
    private NekoModulePipelineCache cache;
    private Context context;
    private NekoScriptModuleLoaderHost host;
    private DefaultErrorTracker tracker;
    private SandboxConfig config;

    @BeforeEach
    void setUp() throws Exception {
        TestPlatformInit.ensureInitialized(GAME_DIR);
        paths = pathsFor(GAME_DIR);
        Files.createDirectories(paths.serverScripts().resolve("src"));
        config = SandboxConfig.defaultConfig();
        compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        compilers.register(NekoTypeScriptLanguagePlugin.INSTANCE);
        compilers.register(NekoJsxLanguagePlugin.INSTANCE);
        compilers.register(NekoTsxLanguagePlugin.INSTANCE);
        compilers.registerLanguage("python", Set.of(".py"), new PythonToJsCompiler());
        tracker = new DefaultErrorTracker(paths, config);
        cache = newCache();
        IOAccess ioAccess = IOAccess.newBuilder()
                .fileSystem(new NekoJSFileSystem(paths.root(), new SandboxPolicy(config, paths), paths, cache))
                .build();
        context = Context.newBuilder("js").allowAllAccess(true).allowIO(ioAccess).build();
        NekoModuleResolver resolver = new NekoModuleResolver(paths.gameDir(), paths.root(), paths.nodeModules(),
                new ScriptFilePolicy(compilers));
        NekoNodeModuleInstaller.install(context, ScriptType.TEST, resolver, paths, tracker, config, cache);
        // Bind the tracker's diagnostic views to the same prepared cache the host uses, with a
        // published generation, exactly like ScriptManager does for a live environment.
        tracker.activateModuleViews(ScriptType.TEST, context, cache, 1L);
        host = (NekoScriptModuleLoaderHost) context.getBindings("js")
                .getMember("__nekoScriptModuleLoaderHost").asHostObject();
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
        cache.close();
    }

    // ---- prepare phase: syntax/transform failures keep the authored location ----

    @Test
    void tsSyntaxFailureRecordsPreparePhaseWithAuthoredLocation() throws Exception {
        // An invalid enum initializer is rejected by the TS frontend with the authored position.
        Path file = write("broken.ts", "enum Mode {\n  Off = 0,\n  Bad = 1e,\n}\n");

        IOException failure = assertPrepareThrows(file);
        record(failure);

        ScriptDiagnosticRecord record = onlyRecord();
        assertEquals(DiagnosticPhase.PREPARE, record.phase());
        assertEquals(NekoModuleError.OWNER_PREPARATION, record.owner());
        assertEquals("server_scripts/src/broken.ts", record.sourcePath().replace('\\', '/'));
        assertTrue(record.line() >= 1, "the TS frontend reports the authored line: " + record.describe());
    }

    @Test
    void pythonSyntaxFailureRecordsPreparePhaseWithAuthoredLocation() throws Exception {
        Path file = write("broken.py", "def f():\n    return 1\n  bad indent\n");

        IOException failure = assertPrepareThrows(file);
        record(failure);

        ScriptDiagnosticRecord record = onlyRecord();
        assertEquals(DiagnosticPhase.PREPARE, record.phase());
        assertEquals(NekoModuleError.OWNER_PREPARATION, record.owner());
        assertEquals("server_scripts/src/broken.py", record.sourcePath().replace('\\', '/'));
    }

    @Test
    void jsxAndTsxTransformFailuresRecordPreparePhase() throws Exception {
        Path jsx = write("broken.jsx", "const v = <div>unclosed;\n");
        IOException jsxFailure = assertPrepareThrows(jsx);
        tracker.clearAll();
        record(jsxFailure);
        assertEquals(DiagnosticPhase.PREPARE, onlyRecord().phase());
        assertEquals("server_scripts/src/broken.jsx", onlyRecord().sourcePath().replace('\\', '/'));

        tracker.clearAll();
        Path tsx = write("broken.tsx", "const v: number = <span>{missing</span>;\n");
        IOException tsxFailure = assertPrepareThrows(tsx);
        record(tsxFailure);
        assertEquals(DiagnosticPhase.PREPARE, onlyRecord().phase());
        assertEquals("server_scripts/src/broken.tsx", onlyRecord().sourcePath().replace('\\', '/'));
    }

    // ---- resolve/link phase ----

    @Test
    void unresolvedEsmImportRecordsResolveLinkPhaseWithSpecifierIdentity() throws Exception {
        write("ghost-entry.mjs", "import { missing } from './ghost.mjs';\nexport const x = missing;\n");

        IOException failure = assertThrowsIOException(() -> host.loadEntry("./server_scripts/src/ghost-entry.mjs"));
        record(failure);

        ScriptDiagnosticRecord record = onlyRecord();
        assertEquals(DiagnosticPhase.RESOLVE_LINK, record.phase(), record.describe());
        assertEquals(NekoModuleError.OWNER_RESOLUTION_CACHE, record.owner());
        assertNotNull(record.moduleIdentity(), record.describe());
    }

    // ---- execution phase: runtime failures map back to authored sources per language ----

    @Test
    void jsEntryRuntimeFailureRecordsExecutionPhaseWithModuleIdentity() throws Exception {
        write("boom.js", "throw new Error('js-boom');\n");

        IOException failure = assertThrowsIOException(() -> host.loadEntry("./server_scripts/src/boom.js"));
        record(failure);

        ScriptDiagnosticRecord record = onlyRecord();
        assertEquals(DiagnosticPhase.EXECUTION, record.phase(), record.describe());
        assertEquals(NekoModuleError.OWNER_EXECUTION, record.owner());
        assertEquals("server_scripts/src/boom.js", record.sourcePath().replace('\\', '/'));
        assertEquals(1, record.line(), "plain JS failures keep the authored throw line");
    }

    @Test
    void tsAndTsxRuntimeFailuresMapBackToAuthoredSourceWithCacheRevision() throws Exception {
        write("boom.ts", "const label: string = 'ts';\nthrow new Error('ts-boom');\n");
        IOException tsFailure = assertThrowsIOException(() -> host.loadEntry("./server_scripts/src/boom.ts"));
        tracker.clearAll();
        record(tsFailure);

        ScriptDiagnosticRecord tsRecord = onlyRecord();
        assertEquals(DiagnosticPhase.EXECUTION, tsRecord.phase(), tsRecord.describe());
        assertEquals("server_scripts/src/boom.ts", tsRecord.sourcePath().replace('\\', '/'),
                "the TS failure must map back to the authored .ts source: " + tsRecord.describe());
        assertEquals(2, tsRecord.line(), tsRecord.describe());
        assertEquals("server_scripts/src/boom.ts", tsRecord.moduleIdentity().replace('\\', '/'),
                tsRecord.describe());

        tracker.clearAll();
        write("boom.tsx", "const label: string = 'tsx';\nconst view = <div>{label}</div>;\nthrow new Error('tsx-boom');\n");
        IOException tsxFailure = assertThrowsIOException(() -> host.loadEntry("./server_scripts/src/boom.tsx"));
        record(tsxFailure);

        ScriptDiagnosticRecord tsxRecord = onlyRecord();
        assertEquals(DiagnosticPhase.EXECUTION, tsxRecord.phase(), tsxRecord.describe());
        assertEquals("server_scripts/src/boom.tsx", tsxRecord.sourcePath().replace('\\', '/'),
                "the TSX failure must map back to the authored .tsx source: " + tsxRecord.describe());
        assertEquals(3, tsxRecord.line(), tsxRecord.describe());
    }

    @Test
    void jsxRuntimeFailureMapsBackToAuthoredJsxSource() throws Exception {
        write("boom.jsx", "const item = { id: 'x' };\nconst view = <div>{item.id}</div>;\nthrow new Error('jsx-boom');\n");
        IOException failure = assertThrowsIOException(() -> host.loadEntry("./server_scripts/src/boom.jsx"));
        record(failure);

        ScriptDiagnosticRecord record = onlyRecord();
        assertEquals(DiagnosticPhase.EXECUTION, record.phase(), record.describe());
        assertEquals("server_scripts/src/boom.jsx", record.sourcePath().replace('\\', '/'),
                "the JSX failure must map back to the authored .jsx source: " + record.describe());
        assertEquals(3, record.line(), record.describe());
        assertNotNull(record.cacheRevision(), record.describe());
    }

    @Test
    void cjsChildRuntimeFailureKeepsChildModuleIdentity() throws Exception {
        write("cjs-boom-child.js", "throw new Error('cjs-child-boom');\n");
        write("cjs-boom-entry.js", "require('./cjs-boom-child.js');\n");
        IOException failure = assertThrowsIOException(() -> host.loadEntry("./server_scripts/src/cjs-boom-entry.js"));
        record(failure);

        ScriptDiagnosticRecord record = onlyRecord();
        assertEquals(DiagnosticPhase.EXECUTION, record.phase(), record.describe());
        assertEquals("server_scripts/src/cjs-boom-child.js", record.sourcePath().replace('\\', '/'),
                "a required CJS child failure must keep the child's identity: " + record.describe());
    }

    @Test
    void pythonRuntimeFailureMapsBackToAuthoredPythonSource() throws Exception {
        write("boom.py", "# comment line\ndef boom():\n    raise RuntimeError('py-boom')\n\nboom()\n");

        IOException failure = assertThrowsIOException(() -> host.loadEntry("./server_scripts/src/boom.py"));
        record(failure);

        ScriptDiagnosticRecord record = onlyRecord();
        assertEquals(DiagnosticPhase.EXECUTION, record.phase(), record.describe());
        assertEquals("server_scripts/src/boom.py", record.sourcePath().replace('\\', '/'),
                "the Python failure must map back to the authored .py source: " + record.describe());
        assertTrue(record.line() >= 2, record.describe());
    }

    @Test
    void preparedCacheRevisionIsRetainedOnMappedDiagnostics() throws Exception {
        // Python is the transpiled language whose compiler emits a real source map, so the
        // prepared cache key is registered together with the mapping. (TS/TSX/JSX erasure
        // preserves authored lines by construction and ships no separate map; their authored
        // identity arrives through the staged module error instead.)
        write("rev-boom.py", "# authored python source\ndef boom():\n    raise RuntimeError('rev-boom')\n\nboom()\n");
        IOException failure = assertThrowsIOException(() -> host.loadEntry("./server_scripts/src/rev-boom.py"));
        record(failure);

        ScriptDiagnosticRecord record = onlyRecord();
        assertEquals("server_scripts/src/rev-boom.py", record.sourcePath().replace('\\', '/'));
        assertTrue(record.line() >= 2, record.describe());
        assertNotNull(record.cacheRevision(),
                "a mapped transpiled diagnostic must retain the prepared cache revision: " + record.describe());
        assertTrue(record.cacheRevision().length() >= 8, record.cacheRevision());

        // Unchanged content keeps a stable cache key across re-preparation.
        Path file = paths.serverScripts().resolve("src/rev-boom.py");
        assertEquals(cache.prepare(file).cacheKey(), cache.prepare(file).cacheKey(),
                "re-preparing unchanged content keeps a stable cache key");
    }

    @Test
    void tsErasureDiagnosticsKeepAuthoredIdentityAndCacheRevision() throws Exception {
        write("erasure-boom.ts", "const one: number = 1;\nthrow new Error('erasure-boom');\n");
        IOException failure = assertThrowsIOException(() -> host.loadEntry("./server_scripts/src/erasure-boom.ts"));
        record(failure);

        ScriptDiagnosticRecord record = onlyRecord();
        assertEquals(DiagnosticPhase.EXECUTION, record.phase(), record.describe());
        assertEquals("server_scripts/src/erasure-boom.ts", record.sourcePath().replace('\\', '/'),
                "erasure keeps authored positions by construction: " + record.describe());
        assertEquals(2, record.line(), record.describe());
        // Erasure ships its (identity) map through the same registry, so the prepared cache
        // revision is retained for TS as well — no invented revision, a real lookup.
        assertNotNull(record.cacheRevision(), record.describe());
    }

    @Test
    void plainJsDiagnosticsCarryThePreparedCacheRevision() throws Exception {
        write("no-rev.js", "throw new Error('js-no-rev');\n");
        IOException failure = assertThrowsIOException(() -> host.loadEntry("./server_scripts/src/no-rev.js"));
        record(failure);

        ScriptDiagnosticRecord record = onlyRecord();
        assertEquals(DiagnosticPhase.EXECUTION, record.phase());
        assertNotNull(record.cacheRevision(),
                "JS prepares through the same registry, so its cache revision is retained: " + record.describe());
    }

    // ---- helpers ----

    private void record(Throwable failure) {
        tracker.recordCallbackError(context, ScriptType.TEST, "matrix", failure);
    }

    private ScriptDiagnosticRecord onlyRecord() {
        assertEquals(1, tracker.getErrorCount(), "exactly one public error is expected");
        ScriptError error = tracker.getAllErrors().iterator().next();
        return error.diagnostic();
    }

    private Path write(String name, String source) throws IOException {
        Path file = paths.serverScripts().resolve("src/" + name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
        return file;
    }

    private IOException assertPrepareThrows(Path file) {
        try {
            cache.prepare(file);
        } catch (IOException failure) {
            return failure;
        }
        throw new AssertionError("expected prepare to fail for " + file);
    }

    private interface ThrowingSupplier {
        Object get() throws Exception;
    }

    private IOException assertThrowsIOException(ThrowingSupplier supplier) {
        try {
            supplier.get();
        } catch (IOException failure) {
            return failure;
        } catch (Exception e) {
            throw new AssertionError("expected IOException, got " + e, e);
        }
        throw new AssertionError("expected IOException");
    }

    private NekoModulePipelineCache newCache() {
        return new NekoModulePipelineCache(
                new NekoModulePipeline(new NekoCompilationPipeline(), compilers, config),
                new SourceMapRegistry(paths.root()), new NekoEsmVirtualModuleRegistry(paths.root()),
                NekoTrustContext.local());
    }

    private static NekoJSPaths pathsFor(Path gameDir) throws Exception {
        Constructor<NekoJSPaths> constructor = NekoJSPaths.class.getDeclaredConstructor(Path.class);
        constructor.setAccessible(true);
        return constructor.newInstance(gameDir);
    }
}
