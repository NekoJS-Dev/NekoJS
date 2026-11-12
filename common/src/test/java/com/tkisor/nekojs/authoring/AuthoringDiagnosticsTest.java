package com.tkisor.nekojs.authoring;

import com.google.gson.JsonObject;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.testing.TestRunReport;
import com.tkisor.nekojs.core.error.DiagnosticPhase;
import com.tkisor.nekojs.core.error.ScriptDiagnosticRecord;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.probe.ProbeConfig;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AuthoringDiagnosticsTest {
    @TempDir Path game;

    private AuthoringRuntime open() throws Exception {
        TestPlatformInit.ensureInitialized();
        NekoJSPaths paths = NekoJSPaths.fromGameDir(game);
        paths.initFolders();
        Files.writeString(paths.config().resolve("ai.toml"), "[documentation]\nenabled = false\n[api]\nenabled = false\n");
        return AuthoringRuntime.open(paths, ProbeConfig.defaultConfig());
    }

    private ScriptDiagnosticRecord record(String id, ScriptType type, String source, String message) {
        return new ScriptDiagnosticRecord(id, type, DiagnosticPhase.EXECUTION, "Script Execution Environment", 42,
                true, source, 7, 3, "private-host-module", "private-cache-revision", message, "private-host-cause-token");
    }

    @Test void projectsExistingFrozenRecordsWithFilteringAndDetachedRelativeLocations() throws Exception {
        var records = new ArrayList<>(List.of(
                record("server-error", ScriptType.SERVER, game.resolve("nekojs/server_scripts/bad.js").toString(), "Untrusted script text"),
                record("client-error", ScriptType.CLIENT, game.getParent().resolve("private-host-file.js").toString(), "Client failure")));
        try (AuthoringRuntime runtime = open()) {
            assertFalse(runtime.execute("errors", new JsonObject()).get("available").getAsBoolean());
            runtime.installDiagnostics(() -> records);
            assertThrows(IllegalStateException.class, () -> runtime.installDiagnostics(List::of));
            JsonObject response = runtime.execute("errors", AuthoringJson.obj("scriptType", "SERVER", "limit", 1));
            assertTrue(response.get("available").getAsBoolean());
            assertTrue(response.get("messagesAreUntrusted").getAsBoolean());
            assertEquals(1, response.get("total").getAsInt());
            JsonObject row = response.getAsJsonArray("errors").get(0).getAsJsonObject();
            assertEquals("server-error", row.get("errorId").getAsString());
            assertEquals("nekojs/server_scripts/bad.js", row.get("sourcePath").getAsString());
            assertEquals("EXECUTION", row.get("phase").getAsString());
            assertEquals(42, row.get("generation").getAsLong());
            assertEquals(7, row.get("line").getAsInt());
            assertEquals(3, row.get("column").getAsInt());
            assertTrue(row.get("recordedDuringCandidate").getAsBoolean());
            assertFalse(response.toString().contains("private-host"));
            assertFalse(response.toString().contains(game.toString().replace("\\", "\\\\")));
            row.addProperty("message", "caller mutation");
            assertEquals("Untrusted script text", runtime.execute("errors", AuthoringJson.obj("scriptType", "SERVER"))
                    .getAsJsonArray("errors").get(0).getAsJsonObject().get("message").getAsString());
            JsonObject client = runtime.execute("errors", AuthoringJson.obj("scriptType", "CLIENT"));
            assertTrue(client.getAsJsonArray("errors").get(0).getAsJsonObject().get("sourcePath").isJsonNull());
            records.clear();
            assertEquals(1, response.getAsJsonArray("errors").size(), "Responses must not retain mutable supplier collections");
            assertEquals(0, runtime.execute("errors", new JsonObject()).get("total").getAsInt());
            assertTrue(runtime.execute("context", new JsonObject()).getAsJsonObject("liveQueries").getAsJsonObject("errors").get("available").getAsBoolean());
        }
    }

    @Test void diagnosticMessagesAndUtf8ResponsesRemainBoundedWithoutHostDetails() throws Exception {
        var rows = Collections.nCopies(200, record("large-error", ScriptType.SERVER, null, "界".repeat(3000)));
        try (AuthoringRuntime runtime = open()) {
            runtime.installDiagnostics(() -> rows);
            JsonObject response = runtime.execute("errors", AuthoringJson.obj("limit", 100));
            assertEquals(200, response.get("total").getAsInt());
            assertTrue(response.get("truncated").getAsBoolean());
            assertTrue(response.get("returned").getAsInt() > 0);
            assertTrue(response.toString().getBytes(StandardCharsets.UTF_8).length <= 20 * 1024);
            for (var item : response.getAsJsonArray("errors")) {
                assertEquals(1024, item.getAsJsonObject().get("message").getAsString().length());
                assertTrue(item.getAsJsonObject().get("messageTruncated").getAsBoolean());
            }
            assertFalse(response.toString().contains("private-host-cause-token"));
            assertFalse(response.toString().contains("private-host-module"));
        }
    }

    @Test void validatesBoundsAndReportsUnavailableSuppliersWithoutExposingTheirFailures() throws Exception {
        AuthoringRuntime runtime = open();
        try (runtime) {
            for (JsonObject request : List.of(AuthoringJson.obj("limit", 0), AuthoringJson.obj("limit", 101),
                    AuthoringJson.obj("scriptType", "WORLD"), AuthoringJson.obj("unexpected", true))) {
                assertEquals(400, assertThrows(AuthoringException.class, () -> runtime.execute("errors", request)).status());
            }
            runtime.installDiagnostics(() -> { throw new IllegalStateException("private-supplier-token"); });
            JsonObject response = runtime.execute("errors", new JsonObject());
            assertFalse(response.get("available").getAsBoolean());
            assertEquals("DIAGNOSTICS_UNAVAILABLE", response.get("reason").getAsString());
            assertFalse(response.toString().contains("private-supplier-token"));
        }
        assertEquals("SERVICE_CLOSED", assertThrows(AuthoringException.class, () -> runtime.execute("errors", new JsonObject())).code());
        assertThrows(IllegalStateException.class, () -> runtime.installDiagnostics(List::of));
    }

    @Test void contextReadsTheLatestTestAttemptWithoutAnAuthoringOperationOrRetainingCallerJson() throws Exception {
        AtomicReference<TestRunReport> latest = new AtomicReference<>();
        try (AuthoringRuntime runtime = open()) {
            runtime.installDiagnostics(List::of, latest::get);
            assertTrue(runtime.execute("context", new JsonObject()).get("testRunReport").isJsonNull());
            latest.set(new TestRunReport(true, true, 0, 1));
            JsonObject failed = runtime.execute("context", new JsonObject()).getAsJsonObject("testRunReport");
            assertTrue(failed.get("executionSucceeded").getAsBoolean());
            assertFalse(failed.get("assertionsPassed").getAsBoolean());
            assertFalse(failed.get("overallPassed").getAsBoolean());
            failed.addProperty("failed", 0);
            assertEquals(1, runtime.execute("context", new JsonObject()).getAsJsonObject("testRunReport").get("failed").getAsInt());
            latest.set(new TestRunReport(true, true, 0, 0));
            JsonObject noAssertions = runtime.execute("context", new JsonObject()).getAsJsonObject("testRunReport");
            assertTrue(noAssertions.get("assertionsPassed").isJsonNull());
            assertFalse(noAssertions.get("overallPassed").getAsBoolean());
            latest.set(new TestRunReport(true, false, 0, 0));
            assertFalse(runtime.execute("context", new JsonObject()).getAsJsonObject("testRunReport").get("executionSucceeded").getAsBoolean());
        }
    }

    @Test void diagnosticScanLimitDoesNotClaimAnExactFilteredTotalBeyondItsCoverage() throws Exception {
        try (AuthoringRuntime runtime = open()) {
            runtime.installDiagnostics(() -> Collections.nCopies(10001, record("many-errors", ScriptType.SERVER, null, "failure")));
            JsonObject response = runtime.execute("errors", AuthoringJson.obj("limit", 1));
            assertEquals(10000, response.get("scanned").getAsInt());
            assertFalse(response.get("totalIsExact").getAsBoolean());
            assertTrue(response.get("total").isJsonNull());
            assertTrue(response.get("truncated").getAsBoolean());
        }
    }
}
