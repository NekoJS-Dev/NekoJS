package com.tkisor.nekojs.core.error;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.ScriptId;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.module.NekoModulePipeline;
import com.tkisor.nekojs.core.module.NekoModulePipelineCache;
import com.tkisor.nekojs.core.module.NekoTrustContext;
import com.tkisor.nekojs.core.module.NekoEsmVirtualModuleRegistry;
import com.tkisor.nekojs.core.module.esm.NekoEsmDiagnostic;
import com.tkisor.nekojs.core.module.esm.NekoEsmLinkException;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ticket 30 reload-boundary guard: the frozen diagnostic record of an error keeps the
 * generation it was recorded under. Old active error history must never be re-attributed to a
 * new candidate generation, and candidate errors must stay staged (never masquerading as the
 * active generation's errors) until the commit point publishes them.
 */
class ScriptDiagnosticGenerationTest {

    @BeforeAll
    static void bindPaths() {
        TestPlatformInit.ensureInitialized();
    }

    private DefaultErrorTracker tracker;

    @BeforeEach
    void newTracker() {
        tracker = new DefaultErrorTracker(NekoJSPaths.get(), SandboxConfig.defaultConfig());
    }

    @Test
    void candidateFailureKeepsActiveHistoryAttributionIntact() throws Exception {
        NekoJSPaths paths = NekoJSPaths.get();
        NekoModulePipelineCache active = newCache(paths);
        NekoModulePipelineCache candidate = newCache(paths);
        try (Context activeContext = Context.newBuilder("js").allowAllAccess(true).build();
             Context candidateContext = Context.newBuilder("js").allowAllAccess(true).build()) {
            // Active generation 5 with one recorded error.
            tracker.activateModuleViews(ScriptType.SERVER, activeContext, active, 5L);
            tracker.recordCallbackError(activeContext, ScriptType.SERVER, "event", esmError(1, 1, "active-boom"));
            ScriptError activeError = onlyError();
            assertEquals(5L, activeError.diagnostic().generation(), "an active error must carry its own generation");
            assertFalse(activeError.diagnostic().recordedDuringCandidate(),
                    "an error recorded on the active environment is not a candidate error");

            // Candidate generation 6 stages its own error; the public view stays generation 5.
            tracker.activateCandidateModuleViews(ScriptType.SERVER, candidateContext, candidate, 6L);
            tracker.recordCallbackError(candidateContext, ScriptType.SERVER, "event", esmError(2, 1, "candidate-boom"));
            assertEquals(1, tracker.getErrorCount(), "candidate errors must stay out of the public count");
            assertEquals("active-boom", onlyError().getErrorMessage());

            // Failed candidate: old history survives with its original attribution.
            tracker.discardCandidateModuleViews(candidateContext);
            ScriptError survivor = onlyError();
            assertEquals("active-boom", survivor.getErrorMessage());
            assertEquals(5L, survivor.diagnostic().generation(),
                    "a discarded candidate must not re-attribute old history to its generation");
            assertFalse(survivor.diagnostic().recordedDuringCandidate());

            // Commit-failure rollback path: publish then restore must also keep the old generation.
            Map<ScriptId, ScriptError> saved = tracker.snapshotType(ScriptType.SERVER);
            tracker.activateCandidateModuleViews(ScriptType.SERVER, candidateContext, candidate, 7L);
            tracker.recordCallbackError(candidateContext, ScriptType.SERVER, "event", esmError(3, 1, "candidate-boom-7"));
            tracker.publishCandidateErrors(ScriptType.SERVER, candidateContext);
            assertEquals(7L, onlyError().diagnostic().generation());
            assertTrue(onlyError().diagnostic().recordedDuringCandidate());
            tracker.restoreType(ScriptType.SERVER, saved);
            assertEquals("active-boom", onlyError().getErrorMessage());
            assertEquals(5L, onlyError().diagnostic().generation(),
                    "restoreType must bring back the old records with their original generation");
        } finally {
            active.close();
            candidate.close();
        }
    }

    @Test
    void committedCandidateErrorsCarryTheCommittedGeneration() throws Exception {
        NekoJSPaths paths = NekoJSPaths.get();
        NekoModulePipelineCache candidate = newCache(paths);
        try (Context candidateContext = Context.newBuilder("js").allowAllAccess(true).build()) {
            tracker.activateCandidateModuleViews(ScriptType.SERVER, candidateContext, candidate, 9L);
            tracker.recordCallbackError(candidateContext, ScriptType.SERVER, "event", esmError(4, 1, "committed-boom"));

            tracker.publishCandidateErrors(ScriptType.SERVER, candidateContext);

            ScriptError published = onlyError();
            assertEquals("committed-boom", published.getErrorMessage());
            assertEquals(9L, published.diagnostic().generation(),
                    "a published candidate error carries the generation that committed");
            assertTrue(published.diagnostic().recordedDuringCandidate(),
                    "the frozen record keeps that the error was observed during candidate build");
        } finally {
            candidate.close();
        }
    }

    @Test
    void typeFallbackAttributionFollowsTheActiveGeneration() {
        NekoModulePipelineCache active = newCache(NekoJSPaths.get());
        tracker.activateModuleViews(ScriptType.SERVER, null, active, 3L);
        try {
            tracker.recordCallbackError(ScriptType.SERVER, "timer", esmError(5, 1, "fallback-boom"));
            ScriptError recorded = onlyError();
            assertEquals(3L, recorded.diagnostic().generation(),
                    "errors recorded without a Context fall back to the type's active generation");
            assertFalse(recorded.diagnostic().recordedDuringCandidate());
        } finally {
            active.close();
        }
    }

    private ScriptError onlyError() {
        assertEquals(1, tracker.getErrorCount(), "exactly one public error is expected");
        return tracker.getAllErrors().iterator().next();
    }

    private static NekoEsmLinkException esmError(int line, int column, String message) {
        return new NekoEsmLinkException(new NekoEsmDiagnostic(null, null, line, column, message));
    }

    private static NekoModulePipelineCache newCache(NekoJSPaths paths) {
        return new NekoModulePipelineCache(
                new NekoModulePipeline(new NekoCompilationPipeline(),
                        ScriptCompilerRegistry.createRuntimeRegistry(), SandboxConfig.defaultConfig()),
                new SourceMapRegistry(paths.root()), new NekoEsmVirtualModuleRegistry(paths.root()),
                NekoTrustContext.local());
    }
}
