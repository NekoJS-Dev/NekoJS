package com.tkisor.nekojs.core.lifecycle;

import com.tkisor.nekojs.api.ScriptType;
import org.junit.jupiter.api.Test;

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
    void testRunDistinguishesUnconfiguredFromCompleted() {
        assertTrue(RuntimeCommandResultFormatter.testResult(NekoRuntimeRoot.TestRunResult.notConfigured())
                .contains("not configured"));
        assertTrue(RuntimeCommandResultFormatter.testResult(NekoRuntimeRoot.TestRunResult.completed())
                .contains("completed"));
    }
}
