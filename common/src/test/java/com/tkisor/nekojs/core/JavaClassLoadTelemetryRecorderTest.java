package com.tkisor.nekojs.core;

import com.tkisor.nekojs.api.ScriptType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ticket 30 telemetry bounds: the bounded recorder dedupes, caps its retention, produces
 * repeatable snapshots, and the telemetry channel stays disable-able (no sink → no records).
 */
class JavaClassLoadTelemetryRecorderTest {

    @AfterEach
    void restoreChannel() {
        JavaClassLoadTelemetry.setSink(null);
        JavaClassLoadTelemetry.exit();
    }

    @Test
    void repeatedObservationsAreCountedNotReAppended() {
        JavaClassLoadTelemetryRecorder recorder = new JavaClassLoadTelemetryRecorder();
        recorder.recordAttempt(ScriptType.SERVER, "server_scripts/a.js", "java.util.ArrayList", false);
        recorder.recordAttempt(ScriptType.SERVER, "server_scripts/a.js", "java.util.ArrayList", false);
        recorder.recordAttempt(ScriptType.SERVER, "server_scripts/a.js", "java.util.ArrayList", false);

        assertEquals(1, recorder.retainedEntries());
        List<JavaClassLoadTelemetryRecorder.Row> rows = recorder.snapshot();
        assertEquals(1, rows.size());
        assertEquals(3L, rows.get(0).count());
        assertEquals(ScriptType.SERVER, rows.get(0).scriptType());
        assertEquals("server_scripts/a.js", rows.get(0).scriptId());
        assertEquals("java.util.ArrayList", rows.get(0).className());
        assertFalse(rows.get(0).allowed());
    }

    @Test
    void retentionIsBoundedAndOldestEntriesEvictFirst() {
        JavaClassLoadTelemetryRecorder recorder = new JavaClassLoadTelemetryRecorder(4);
        for (int i = 0; i < 6; i++) {
            recorder.recordAttempt(ScriptType.TEST, "test_scripts/t" + i + ".js", "java.lang.Math", true);
        }

        assertEquals(4, recorder.retainedEntries(), "retention must be capped at the configured bound");
        List<String> ids = recorder.snapshot().stream().map(JavaClassLoadTelemetryRecorder.Row::scriptId).toList();
        assertEquals(List.of("test_scripts/t2.js", "test_scripts/t3.js", "test_scripts/t4.js", "test_scripts/t5.js"),
                ids, "the oldest entries must be evicted first");
    }

    @Test
    void snapshotsAreRepeatableUntilNewObservationsArrive() {
        JavaClassLoadTelemetryRecorder recorder = new JavaClassLoadTelemetryRecorder();
        recorder.recordLoad(ScriptType.CLIENT, "client_scripts/c.js", "java.util.HashMap");

        assertEquals(recorder.snapshot(), recorder.snapshot(),
                "two consecutive snapshots without new observations must be equal");

        recorder.recordLoad(ScriptType.CLIENT, "client_scripts/c.js", "java.util.HashMap");
        assertEquals(2L, recorder.snapshot().get(0).count());
    }

    @Test
    void telemetryChannelIsDisabledWithoutASink() {
        JavaClassLoadTelemetry.setSink(null);
        assertFalse(JavaClassLoadTelemetry.isEnabled(), "no sink installed means telemetry is disabled");

        // Scopes without a sink must not record anything observable.
        JavaClassLoadTelemetry.enter(ScriptType.SERVER, "server_scripts/a.js");
        try {
            JavaClassLoadTelemetry.recordAttempt("java.util.ArrayList", false);
        } finally {
            JavaClassLoadTelemetry.exit();
        }
        assertFalse(JavaClassLoadTelemetry.isEnabled());
    }

    @Test
    void recorderRoundTripsThroughTheTelemetryChannel() {
        JavaClassLoadTelemetryRecorder recorder = new JavaClassLoadTelemetryRecorder();
        JavaClassLoadTelemetry.setSink(recorder);
        assertTrue(JavaClassLoadTelemetry.isEnabled());

        JavaClassLoadTelemetry.enter(ScriptType.SERVER, "server_scripts/scope.js");
        try {
            JavaClassLoadTelemetry.recordAttempt("java.util.regex.Pattern", true);
        } finally {
            JavaClassLoadTelemetry.exit();
        }

        List<JavaClassLoadTelemetryRecorder.Row> rows = recorder.snapshot();
        assertEquals(1, rows.size());
        assertEquals("server_scripts/scope.js", rows.get(0).scriptId());
        assertTrue(rows.get(0).allowed());

        // Re-installing null disables the channel again; exiting scopes record nothing further.
        JavaClassLoadTelemetry.setSink(null);
        assertFalse(JavaClassLoadTelemetry.isEnabled());
    }
}
