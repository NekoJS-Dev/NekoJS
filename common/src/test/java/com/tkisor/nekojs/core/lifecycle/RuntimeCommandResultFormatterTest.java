package com.tkisor.nekojs.core.lifecycle;

import com.tkisor.nekojs.api.ScriptType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeCommandResultFormatterTest {

    @Test
    void reloadSuccessReportsCommitAndStartupBoundary() {
        var server = new NekoRuntimeRoot.ReloadResult(
                ScriptType.SERVER, true, null, 12, ReloadPhase.COMMIT, null);
        assertTrue(RuntimeCommandResultFormatter.reloadResult(server, false)
                .contains("server reload committed: generation=12 phase=COMMIT"));

        var startup = NekoRuntimeRoot.ReloadResult.successNonTransactional(
                ScriptType.STARTUP, 4, ReloadPhase.STARTUP);
        String startupText = RuntimeCommandResultFormatter.reloadResult(startup, false);
        assertTrue(startupText.contains("non-transactionally"));
        assertTrue(startupText.contains("restart the game/loader"));
    }

    @Test
    void failuresIncludeStageSourceOwnerAndActiveStateWithoutStackTrace() {
        var report = new ReloadFailureReport(
                ScriptType.SERVER, 13, ReloadPhase.EXECUTION, "server_scripts/broken.js",
                "ScriptManager[SERVER]", "script-execution",
                new IllegalStateException("candidate failed"));

        String retained = RuntimeCommandResultFormatter.reloadFailure(report, false);
        assertTrue(retained.contains("generation=13 phase=EXECUTION"));
        assertTrue(retained.contains("source=server_scripts/broken.js"));
        assertTrue(retained.contains("owner=ScriptManager[SERVER]"));
        assertTrue(retained.contains("candidate was discarded"));
        assertFalse(retained.contains("\tat "));

        String isolated = RuntimeCommandResultFormatter.reloadFailure(report, true);
        assertTrue(isolated.contains("active generation remains isolated"));
        assertTrue(isolated.contains("explicit full reload is required"));
    }

    @Test
    void postCommitFailureKeepsTheCommittedGenerationExplicit() {
        var committed = new NekoRuntimeRoot.ReloadResult(
                ScriptType.SERVER, true, null, 12, ReloadPhase.COMMIT, null);

        String message = RuntimeCommandResultFormatter.postReloadFailure(committed, "recipe/pack processing",
                new IllegalStateException("resource follow-up error"));
        assertTrue(message.contains("server reload committed (generation=12 phase=COMMIT)"));
        assertTrue(message.contains("post-reload recipe/pack processing failed"));
        assertTrue(message.contains("resource follow-up error"));
        assertFalse(message.contains("IllegalStateException"),
                "user-facing failure text must carry the message only, not the exception class name (F2)");
        assertFalse(message.startsWith("reload failed"));
    }

    @Test
    void singleFileReloadFailureReportsMessageWithoutExceptionClassName() {
        var failure = NekoRuntimeRoot.ReloadResult.failure(ScriptType.SERVER, 3, ReloadPhase.FILE,
                "server_scripts/t20-single.js", new java.io.IOException("Unsupported or missing script file: x"));

        String message = RuntimeCommandResultFormatter.reloadResult(failure, false);
        assertTrue(message.contains("phase=FILE source=server_scripts/t20-single.js"));
        assertTrue(message.contains("error=Unsupported or missing script file: x"));
        assertFalse(message.contains("java.io.IOException"),
                "user-facing failure text must not embed the exception class name (F2)");
        assertTrue(message.contains("active runtime was not switched"),
                "single-file failure keeps its consequence sentence");
    }

    @Test
    void failureReportsFallBackToSimpleClassNameWhenMessageIsMissing() {
        var noMessage = NekoRuntimeRoot.ReloadResult.failure(ScriptType.SERVER, 3, ReloadPhase.PREPARATION,
                null, new IllegalStateException());
        assertTrue(RuntimeCommandResultFormatter.reloadResult(noMessage, false).contains("error=IllegalStateException"));

        var report = new ReloadFailureReport(
                ScriptType.SERVER, 13, ReloadPhase.EXECUTION, "server_scripts/broken.js",
                "ScriptManager[SERVER]", "script-execution",
                new IllegalStateException("candidate failed"));
        assertFalse(report.describe().contains("java.lang.IllegalStateException"),
                "candidate failure descriptions must also stay message-only (F2)");
    }

    @Test
    void testRunKeepsExecutionAndAssertionOutcomesSeparate() {
        var caughtFailure = new NekoRuntimeRoot.TestRunResult(true, true,
                new com.tkisor.nekojs.api.testing.TestRunReport(true, true, 2, 1));
        assertTrue(RuntimeCommandResultFormatter.testResult(caughtFailure).contains("executionSucceeded=true"));
        assertTrue(RuntimeCommandResultFormatter.testResult(caughtFailure).contains("assertions failed"));
        assertFalse(RuntimeCommandResultFormatter.testExecutionSucceeded(caughtFailure));
        assertEquals("nekojs.command.test.assertions_failed", RuntimeCommandResultFormatter.testResultKey(caughtFailure));
        var noAssertions = new NekoRuntimeRoot.TestRunResult(true, true,
                new com.tkisor.nekojs.api.testing.TestRunReport(true, true, 0, 0));
        assertTrue(RuntimeCommandResultFormatter.testResult(noAssertions).contains("assertions not observed"));
        assertTrue(RuntimeCommandResultFormatter.testExecutionSucceeded(noAssertions));
        assertEquals("nekojs.command.test.no_assertions", RuntimeCommandResultFormatter.testResultKey(noAssertions));
        var passed = new NekoRuntimeRoot.TestRunResult(true, true,
                new com.tkisor.nekojs.api.testing.TestRunReport(true, true, 2, 0));
        assertTrue(RuntimeCommandResultFormatter.testResult(passed).contains("assertions passed"));
        assertEquals("nekojs.command.test.passed", RuntimeCommandResultFormatter.testResultKey(passed));
        var failedExecution = new NekoRuntimeRoot.TestRunResult(true, true,
                new com.tkisor.nekojs.api.testing.TestRunReport(true, false, 1, 0));
        assertFalse(RuntimeCommandResultFormatter.testExecutionSucceeded(failedExecution));
        assertEquals("nekojs.command.test.execution_failed", RuntimeCommandResultFormatter.testResultKey(failedExecution));
    }

    @Test
    void testRunDistinguishesUnconfiguredFromCompleted() {
        assertTrue(RuntimeCommandResultFormatter.testResult(NekoRuntimeRoot.TestRunResult.notConfigured())
                .contains("not configured"));
        assertTrue(RuntimeCommandResultFormatter.testResult(NekoRuntimeRoot.TestRunResult.completed())
                .contains("completed"));
    }
}
