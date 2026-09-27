package com.tkisor.nekojs.core.error;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.network.ErrorSummaryDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ticket 27 AC1: the read-only error GUI's local-positioning action consumes ticket 30's
 * {@link DiagnosticOpenAction} non-GUI seam — the frozen record's {@code openAction()} round
 * trips through {@code payload()/parse()} and is resolved and dispatched by
 * {@link ErrorOpenService}/{@link LocalErrorSource}; the DTO (wire projection) and the seam
 * share one validation implementation (no second facts source); missing locations, remote
 * servers and virtual paths are reported explicitly, never guessed.
 */
class Ticket27DiagnosticOpenActionConsumeTest {
    @TempDir
    Path gameDir;

    private NekoJSPaths paths;
    private Path file;
    private RecordingOpener opener;
    private ErrorOpenService service;
    private LocalErrorSource source;

    @BeforeEach
    void setUp() throws IOException {
        paths = NekoJSPaths.fromGameDir(gameDir);
        Files.createDirectories(paths.serverScripts());
        file = paths.serverScripts().resolve("open.js");
        Files.writeString(file, "throw new Error('x')");
        opener = new RecordingOpener();
        source = LocalErrorSource.forPaths(paths);
        service = new ErrorOpenService(source, opener);
    }

    /** A frozen record with a real authored location under the script root. */
    private ScriptDiagnosticRecord locatedRecord(String relativePath, int line) {
        return new ScriptDiagnosticRecord(
                "nekojs/rt/server_scripts/open.js", ScriptType.SERVER, DiagnosticPhase.EXECUTION,
                "Script Execution", 5, false, relativePath, line, 3,
                "server_scripts/open.js", null, "boom: failure", "Error: boom: failure");
    }

    @Test
    void recordOpenActionRoundTripsAndDispatchesTheVerifiedLocalFile() throws IOException {
        ScriptDiagnosticRecord record = locatedRecord(relative(file), 7);
        DiagnosticOpenAction action = record.openAction();
        assertNotNull(action, "a located record must produce an open action");

        // The GUI consumes the parseable seam form, so the round-trip must be lossless.
        assertEquals(action, DiagnosticOpenAction.parse(action.payload()));

        ErrorOpenService.Result result = service.openAsync(action, true, Runnable::run).join();

        assertEquals(ErrorOpenService.Outcome.DISPATCH_ACCEPTED, result.outcome());
        assertTrue(result.accepted());
        assertTrue(result.lineRequested(), "the record's line drives the goto request");
        assertEquals(LocalErrorSource.Status.LOCAL_FILE, result.locationStatus());
        assertEquals(1, opener.targets.size());
        assertEquals(file.toRealPath(), opener.targets.getFirst().file());
    }

    @Test
    void seamAndWireProjectionResolveTheSameLocation() throws IOException {
        ScriptDiagnosticRecord record = locatedRecord(relative(file), 7);
        // The dashboard packet is assembled from the same record (ticket 30 single projection):
        // the display path of a located error is the record's authored source path.
        ErrorSummaryDTO dto = record.toErrorSummary(2, record.sourcePath(), "legacy message", "full details");

        LocalErrorSource.Result viaWire = source.resolve(dto, true);
        LocalErrorSource.Result viaSeam = source.resolve(record.openAction(), true);

        assertEquals(viaWire, viaSeam,
                "the wire projection and the diagnostic seam must resolve identically:"
                        + " one validation implementation, one facts source");
        assertTrue(viaSeam.available());
        assertEquals(file.toRealPath(), viaSeam.target().file());
    }

    @Test
    void recordWithoutLocationYieldsNoActionAndOpeningReportsUnavailable() {
        ScriptDiagnosticRecord record = locatedRecord(null, -1);
        assertNull(record.openAction(), "a record without a locatable source has no open action");

        ErrorOpenService.Result result = service.openAsync(record.openAction(), true, Runnable::run).join();

        assertEquals(ErrorOpenService.Outcome.LOCATION_UNAVAILABLE, result.outcome());
        assertEquals(LocalErrorSource.Status.NO_ERROR, result.locationStatus());
        assertTrue(opener.targets.isEmpty(), "an unresolvable action must not reach the opener");
    }

    @Test
    void remoteAndVirtualSourcesAreReportedNotGuessed() {
        ScriptDiagnosticRecord remote = locatedRecord(relative(file), 7);
        ErrorOpenService.Result remoteResult =
                service.openAsync(remote.openAction(), false, Runnable::run).join();
        assertEquals(ErrorOpenService.Outcome.LOCATION_UNAVAILABLE, remoteResult.outcome());
        assertEquals(LocalErrorSource.Status.REMOTE_SERVER, remoteResult.locationStatus());
        assertTrue(opener.targets.isEmpty());

        ScriptDiagnosticRecord virtual = locatedRecord("<virtual:graal-module>", 3);
        LocalErrorSource.Result virtualResult = source.resolve(virtual.openAction(), true);
        assertEquals(LocalErrorSource.Status.VIRTUAL_PATH, virtualResult.status());
    }

    private String relative(Path target) {
        return paths.root().relativize(target).toString().replace('\\', '/');
    }

    private static final class RecordingOpener implements ErrorLocationOpener {
        private final List<LocalErrorSource.Target> targets = new ArrayList<>();

        @Override
        public OpenResult open(LocalErrorSource.Target target) {
            targets.add(target);
            return OpenResult.accepted(target.gotoLine());
        }
    }
}
