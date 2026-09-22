package com.tkisor.nekojs.core.error;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.NekoModuleMode;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.lifecycle.NekoReloadException;
import com.tkisor.nekojs.core.lifecycle.ReloadFailureReport;
import com.tkisor.nekojs.core.lifecycle.ReloadPhase;
import com.tkisor.nekojs.core.module.NekoModuleError;
import com.tkisor.nekojs.core.module.NekoModulePipeline;
import com.tkisor.nekojs.core.module.NekoModulePipelineCache;
import com.tkisor.nekojs.core.module.NekoTrustContext;
import com.tkisor.nekojs.core.module.esm.NekoEsmDiagnostic;
import com.tkisor.nekojs.core.module.esm.NekoEsmLinkException;
import com.tkisor.nekojs.core.module.NekoEsmVirtualModuleRegistry;
import com.tkisor.nekojs.network.ErrorSummaryDTO;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import com.tkisor.nekojs.core.SyncEvalWatchdog;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.PolyglotException;
import graal.graalvm.polyglot.Source;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ticket 30 frozen diagnostic record contract: phase/owner classification for representative
 * failures, source-map projection back to the authored source with module identity and cache
 * revision, and the shared user-visible projections (summary DTO, open action payload).
 */
class ScriptDiagnosticRecordTest {

    @BeforeAll
    static void bindPaths() {
        TestPlatformInit.ensureInitialized();
    }

    private DefaultErrorTracker tracker;
    private NekoJSPaths paths;

    @BeforeEach
    void newTracker() {
        TestPlatformInit.ensureInitialized();
        paths = NekoJSPaths.get();
        tracker = new DefaultErrorTracker(paths, SandboxConfig.defaultConfig());
    }

    // ---- source-map phase mapping (red-first assertion for ticket 30) ----

    @Test
    void mappedExecutionErrorKeepsOriginalSourceModuleIdentityAndCacheRevision() throws Exception {
        SourceMapRegistry maps = new SourceMapRegistry(paths.root());
        // Generated line 2 maps back to authored line 5 of the original TS source.
        maps.register("server_scripts/gen.js",
                "{\"version\":3,\"sources\":[\"server_scripts/authored.ts\"],\"sourcesContent\":[null],"
                        + "\"names\":[],\"mappings\":\"AAAA;AAIA\"}",
                0, "cache-key-rev-1");
        NekoModulePipelineCache cache = newCache(maps);
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            tracker.activateModuleViews(ScriptType.SERVER, context, cache, 2L);

            PolyglotException failure = throwingPolyglot(context, "server_scripts/gen.js");
            tracker.recordCallbackError(context, ScriptType.SERVER, "event", failure);

            ScriptError error = onlyError();
            ScriptDiagnosticRecord record = error.diagnostic();
            assertEquals(DiagnosticPhase.EXECUTION, record.phase());
            assertEquals(NekoModuleError.OWNER_EXECUTION, record.owner());
            assertEquals(2L, record.generation());
            assertEquals("server_scripts/authored.ts", record.sourcePath(),
                    "the mapped position must point at the original authored source");
            assertEquals(5, record.line(), "the original authored line must survive the mapping");
            assertTrue(record.column() > 0);
            assertEquals("server_scripts/gen.js", record.moduleIdentity(),
                    "the executing generated module identity must be kept next to the mapped source");
            assertEquals("cache-key-rev-1", record.cacheRevision(),
                    "the cache revision of the prepared module that produced the mapping must be retained");
        } finally {
            cache.close();
        }
    }

    // ---- phase matrix: representative failures classify with phase and owner ----

    @Test
    void prepareStageErrorClassifiesAsPrepareWithPreparationOwner() {
        NekoModuleError prepare = NekoModuleError.prepare("server_scripts/broken.ts", "typescript",
                NekoModuleMode.ESM, 4, 7, "SyntaxError: unexpected token", null);
        tracker.recordCallbackError(ScriptType.SERVER, "prepare", new java.io.IOException(prepare.detail(), prepare));

        ScriptDiagnosticRecord record = onlyError().diagnostic();
        assertEquals(DiagnosticPhase.PREPARE, record.phase());
        assertEquals(NekoModuleError.OWNER_PREPARATION, record.owner());
        assertEquals("server_scripts/broken.ts", record.sourcePath());
        assertEquals(4, record.line());
        assertEquals(7, record.column());
        assertNotNull(record.cause());
        assertFalse(record.cause().isBlank());
    }

    @Test
    void trustDenialClassifiesAsTrustWithPackTrustOwner() {
        NekoModuleError denied = NekoModuleError.denied("packs/remote/server_scripts/entry.js", "javascript",
                NekoModuleMode.ESM, null, "no explicit authorization for this remote source");
        tracker.recordCallbackError(ScriptType.SERVER, "prepare", new java.io.IOException(denied.detail(), denied));

        ScriptDiagnosticRecord record = onlyError().diagnostic();
        assertEquals(DiagnosticPhase.TRUST, record.phase());
        assertEquals(NekoModuleError.OWNER_PACK_TRUST, record.owner());
        assertEquals("packs/remote/server_scripts/entry.js", record.sourcePath());
    }

    @Test
    void resolveAndLinkErrorsClassifyAsResolveLinkWithResolutionOwner() {
        NekoModuleError resolve = NekoModuleError.resolve("server_scripts/entry.mjs", "./ghost.mjs",
                new java.io.IOException("Cannot resolve module: ./ghost.mjs"));
        tracker.recordCallbackError(ScriptType.SERVER, "prepare", new java.io.IOException(resolve.detail(), resolve));
        ScriptDiagnosticRecord record = onlyError().diagnostic();
        assertEquals(DiagnosticPhase.RESOLVE_LINK, record.phase());
        assertEquals(NekoModuleError.OWNER_RESOLUTION_CACHE, record.owner());
        assertEquals("server_scripts/entry.mjs", record.sourcePath());
        assertEquals("./ghost.mjs", record.moduleIdentity());

        tracker.clearAll();
        NekoEsmLinkException link = new NekoEsmLinkException(new NekoEsmDiagnostic(
                paths.root().resolve("server_scripts/linked.mjs"), null, 12, 3, "unresolved export 'x'"));
        tracker.recordCallbackError(ScriptType.SERVER, "prepare", link);
        ScriptDiagnosticRecord linkRecord = onlyError().diagnostic();
        assertEquals(DiagnosticPhase.RESOLVE_LINK, linkRecord.phase());
        assertEquals(NekoModuleError.OWNER_RESOLUTION_CACHE, linkRecord.owner());
        assertTrue(linkRecord.sourcePath().replace('\\', '/').endsWith("server_scripts/linked.mjs"),
                linkRecord.sourcePath());
        assertEquals(12, linkRecord.line());
        assertEquals(3, linkRecord.column());
    }

    @Test
    void cacheStageErrorClassifiesAsCache() {
        NekoModuleError cache = NekoModuleError.cache("server_scripts/stale.ts",
                "Cannot stamp module source: read failed", null);
        tracker.recordCallbackError(ScriptType.SERVER, "prepare", new java.io.IOException(cache.detail(), cache));

        ScriptDiagnosticRecord record = onlyError().diagnostic();
        assertEquals(DiagnosticPhase.CACHE, record.phase());
        assertEquals(NekoModuleError.OWNER_RESOLUTION_CACHE, record.owner());
        assertEquals("server_scripts/stale.ts", record.sourcePath());
    }

    @Test
    void syntaxErrorPolyglotClassifiesAsPrepare() {
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            PolyglotException syntaxError = syntaxPolyglot(context);
            tracker.recordCallbackError(context, ScriptType.SERVER, "event", syntaxError);

            ScriptDiagnosticRecord record = onlyError().diagnostic();
            assertEquals(DiagnosticPhase.PREPARE, record.phase(),
                    "a guest syntax error is a prepare-phase failure even when raised at eval time");
            assertEquals(NekoModuleError.OWNER_PREPARATION, record.owner());
        }
    }

    @Test
    void cancelledEvaluationClassifiesAsWatchdog() {
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            SyncEvalWatchdog.Guard guard = SyncEvalWatchdog.arm(context, 1);
            PolyglotException cancelled;
            try {
                context.eval("js", "while (true) { /* spin */ }");
                throw new AssertionError("expected the watchdog to cancel the evaluation");
            } catch (PolyglotException e) {
                cancelled = e;
            } finally {
                guard.disarm();
            }
            assertTrue(cancelled.isCancelled() || cancelled.isInterrupted(),
                    "the runaway watchdog must cancel the guest evaluation");

            tracker.recordCallbackError(ScriptType.SERVER, "event", cancelled);
            ScriptDiagnosticRecord record = onlyError().diagnostic();
            assertEquals(DiagnosticPhase.WATCHDOG, record.phase());
            assertEquals(ScriptDiagnosticRecord.OWNER_WATCHDOG, record.owner());
        }
    }

    @Test
    void reloadFailureProjectsAsReloadCancelRecord() {
        ReloadFailureReport report = new ReloadFailureReport(ScriptType.SERVER, 8L, ReloadPhase.EXECUTION,
                "server_scripts/entry.js", "ScriptManager[SERVER]", "script-execution",
                new IllegalStateException("boom"));
        ScriptDiagnosticRecord record = ScriptDiagnosticRecord.ofReloadFailure(
                new NekoReloadException(report));

        assertEquals(DiagnosticPhase.RELOAD_CANCEL, record.phase());
        assertEquals(ScriptDiagnosticRecord.OWNER_RUNTIME_RELOAD, record.owner());
        assertEquals(8L, record.generation());
        assertEquals(ScriptType.SERVER, record.scriptType());
        assertEquals("server_scripts/entry.js", record.sourcePath());
        assertTrue(record.recordedDuringCandidate(), "reload failures are observed on the candidate side");
    }

    // ---- shared projections ----

    @Test
    void summaryDtoProjectionCarriesTheFrozenCoreFields() throws Exception {
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            tracker.recordCallbackError(context, ScriptType.SERVER, "event",
                    throwingPolyglot(context, "server_scripts/boom.js"));
            ScriptError error = onlyError();
            ScriptDiagnosticRecord record = error.diagnostic();
            assertEquals("server_scripts/boom.js", record.sourcePath());

            ErrorSummaryDTO dto = record.toErrorSummary(error.getOccurrenceCount(),
                    error.getDisplayPath(), error.getFullDetailText());
            assertEquals(record.errorId(), dto.id());
            assertEquals(error.getDisplayPath(), dto.path());
            assertEquals(record.line(), dto.line());
            assertEquals(error.getOccurrenceCount(), dto.count());
            assertEquals(error.getErrorMessage(), dto.message());
            assertEquals(error.getFullDetailText(), dto.fullDetails());
        }
    }

    @Test
    void openActionPayloadIsParseableAndRoundTrips() throws Exception {
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            tracker.recordCallbackError(context, ScriptType.SERVER, "event",
                    throwingPolyglot(context, "server_scripts/boom.js"));
            ScriptDiagnosticRecord record = onlyError().diagnostic();

            DiagnosticOpenAction action = record.openAction();
            assertNotNull(action, "an error with a real source path must yield an open action");
            assertEquals(ScriptType.SERVER, action.scriptType());
            assertEquals(record.owner(), action.owner());
            assertEquals(record.generation(), action.generation());
            assertEquals(record.sourcePath(), action.sourcePath());
            assertEquals(record.line(), action.line());
            assertEquals(record.column(), action.column());

            String payload = action.payload();
            DiagnosticOpenAction parsed = DiagnosticOpenAction.parse(payload);
            assertEquals(action, parsed, "the payload must round-trip through its parseable form: " + payload);
            assertTrue(payload.contains("type=server"), payload);
            assertTrue(payload.contains("source=server_scripts/boom.js"), payload);
        }
    }

    @Test
    void unknownSourcePathYieldsNoOpenAction() {
        tracker.recordCallbackError(ScriptType.SERVER, "event",
                new RuntimeException("no location available"));
        ScriptDiagnosticRecord record = onlyError().diagnostic();
        assertNull(record.sourcePath(), "an error without a resolvable location has no source path");
        assertNull(record.openAction(), "unknown placeholder paths must not produce an open action");
    }

    // ---- helpers ----

    private ScriptError onlyError() {
        assertEquals(1, tracker.getErrorCount(), "exactly one public error is expected");
        return tracker.getAllErrors().iterator().next();
    }

    private static NekoEsmLinkException esmError(int line, int column, String message) {
        return new NekoEsmLinkException(new NekoEsmDiagnostic(
                null, null, line, column, message));
    }

    private static PolyglotException throwingPolyglot(Context context, String sourceName) throws Exception {
        try {
            context.eval(Source.newBuilder("js", ";\nthrow new Error('mapped failure');", sourceName).build());
            throw new AssertionError("expected PolyglotException");
        } catch (PolyglotException e) {
            return e;
        }
    }

    private static PolyglotException syntaxPolyglot(Context context) {
        try {
            context.eval("js", "function broken( {");
            throw new AssertionError("expected a syntax error");
        } catch (PolyglotException e) {
            return e;
        }
    }

    private NekoModulePipelineCache newCache(SourceMapRegistry maps) {
        return new NekoModulePipelineCache(
                new NekoModulePipeline(new NekoCompilationPipeline(),
                        ScriptCompilerRegistry.createRuntimeRegistry(), SandboxConfig.defaultConfig()),
                maps, new NekoEsmVirtualModuleRegistry(paths.root()), NekoTrustContext.local());
    }
}
