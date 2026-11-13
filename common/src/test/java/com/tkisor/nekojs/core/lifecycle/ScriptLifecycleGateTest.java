package com.tkisor.nekojs.core.lifecycle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit checks for the owner-thread scheduling gate: entries run one at a time, reload is not
 * reentrant, LOAD nests inside RELOAD (STARTUP reset plus load), requests from inside a callback
 * are rejected, close wins over other operations and is rejected after shutdown, a same-thread
 * nested close is rejected, and the isolation-failure mark is only cleared by an explicit reset.
 */
class ScriptLifecycleGateTest {

    @Test
    void reloadEnterExitSerial() {
        ScriptLifecycleGate gate = new ScriptLifecycleGate();
        assertEquals(ScriptLifecycleGate.Decision.EXECUTED,
                gate.tryEnter(ScriptLifecycleGate.Operation.RELOAD));
        assertTrue(gate.isInLifecycle());
        gate.exit(ScriptLifecycleGate.Operation.RELOAD);
        assertFalse(gate.isInLifecycle());
        assertEquals(ScriptLifecycleGate.Decision.EXECUTED,
                gate.tryEnter(ScriptLifecycleGate.Operation.RELOAD));
        gate.exit(ScriptLifecycleGate.Operation.RELOAD);
    }

    @Test
    void reloadIsNotReentrantOnSameThread() {
        ScriptLifecycleGate gate = new ScriptLifecycleGate();
        assertEquals(ScriptLifecycleGate.Decision.EXECUTED,
                gate.tryEnter(ScriptLifecycleGate.Operation.RELOAD));
        assertEquals(ScriptLifecycleGate.Decision.REJECTED_REENTRANT,
                gate.tryEnter(ScriptLifecycleGate.Operation.RELOAD));
        // One nested LOAD inside RELOAD is the existing shape of the STARTUP reset plus load path (see
        // loadNestedInReloadIsAllowedForStartupResetPath); this test only shows that RELOAD itself
        // is not reentrant.
        gate.exit(ScriptLifecycleGate.Operation.RELOAD);
    }

    @Test
    void loadNestedInReloadIsAllowedForStartupResetPath() {
        ScriptLifecycleGate gate = new ScriptLifecycleGate();
        assertEquals(ScriptLifecycleGate.Decision.EXECUTED,
                gate.tryEnter(ScriptLifecycleGate.Operation.RELOAD));
        assertEquals(ScriptLifecycleGate.Decision.EXECUTED,
                gate.tryEnter(ScriptLifecycleGate.Operation.LOAD));
        // A LOAD inside the LOAD nested in RELOAD is still rejected: only the one existing nesting level
        // is allowed.
        assertEquals(ScriptLifecycleGate.Decision.REJECTED_REENTRANT,
                gate.tryEnter(ScriptLifecycleGate.Operation.LOAD));
        gate.exit(ScriptLifecycleGate.Operation.LOAD);
        gate.exit(ScriptLifecycleGate.Operation.RELOAD);
        assertFalse(gate.isInLifecycle());
    }

    @Test
    void loadInsideLoadIsRejected() {
        ScriptLifecycleGate gate = new ScriptLifecycleGate();
        assertEquals(ScriptLifecycleGate.Decision.EXECUTED,
                gate.tryEnter(ScriptLifecycleGate.Operation.LOAD));
        assertEquals(ScriptLifecycleGate.Decision.REJECTED_REENTRANT,
                gate.tryEnter(ScriptLifecycleGate.Operation.LOAD));
        gate.exit(ScriptLifecycleGate.Operation.LOAD);
    }

    @Test
    void reloadInsideManagedCallbackIsRejected() {
        ScriptLifecycleGate gate = new ScriptLifecycleGate();
        ScriptLifecycleGate.enterCallback();
        try {
            assertEquals(ScriptLifecycleGate.Decision.REJECTED_REENTRANT,
                    gate.tryEnter(ScriptLifecycleGate.Operation.RELOAD));
            assertEquals(ScriptLifecycleGate.Decision.REJECTED_REENTRANT,
                    gate.tryEnter(ScriptLifecycleGate.Operation.LOAD));
        } finally {
            ScriptLifecycleGate.exitCallback();
        }
        assertFalse(gate.isInLifecycle());
        assertEquals(ScriptLifecycleGate.Decision.EXECUTED,
                gate.tryEnter(ScriptLifecycleGate.Operation.RELOAD));
        gate.exit(ScriptLifecycleGate.Operation.RELOAD);
    }

    @Test
    void nestedCallbackDepthKeepsRejectionUntilOutermostExit() {
        ScriptLifecycleGate gate = new ScriptLifecycleGate();
        ScriptLifecycleGate.enterCallback();
        ScriptLifecycleGate.enterCallback();
        assertEquals(ScriptLifecycleGate.Decision.REJECTED_REENTRANT,
                gate.tryEnter(ScriptLifecycleGate.Operation.RELOAD));
        ScriptLifecycleGate.exitCallback();
        // Still inside the outer callback, so the request is still rejected.
        assertEquals(ScriptLifecycleGate.Decision.REJECTED_REENTRANT,
                gate.tryEnter(ScriptLifecycleGate.Operation.RELOAD));
        ScriptLifecycleGate.exitCallback();
        assertEquals(ScriptLifecycleGate.Decision.EXECUTED,
                gate.tryEnter(ScriptLifecycleGate.Operation.RELOAD));
        gate.exit(ScriptLifecycleGate.Operation.RELOAD);
    }

    @Test
    void closeIsPrioritizedOverNotStartedReload() {
        ScriptLifecycleGate gate = new ScriptLifecycleGate();
        gate.requestClose();
        assertTrue(gate.isCloseRequested());
        assertEquals(ScriptLifecycleGate.Decision.REJECTED_CLOSING,
                gate.tryEnter(ScriptLifecycleGate.Operation.RELOAD));
        assertEquals(ScriptLifecycleGate.Decision.REJECTED_CLOSING,
                gate.tryEnter(ScriptLifecycleGate.Operation.LOAD));
        assertEquals(ScriptLifecycleGate.Decision.EXECUTED,
                gate.tryEnter(ScriptLifecycleGate.Operation.CLOSE));
        gate.exit(ScriptLifecycleGate.Operation.CLOSE);
        gate.markClosed();
    }

    @Test
    void closeNestedInLifecycleIsRejectedNotInline() {
        ScriptLifecycleGate gate = new ScriptLifecycleGate();
        assertEquals(ScriptLifecycleGate.Decision.EXECUTED,
                gate.tryEnter(ScriptLifecycleGate.Operation.RELOAD));
        // A same-thread close while the lifecycle is running only sets the preemption flag; it does not
        // tear anything down inline.
        assertEquals(ScriptLifecycleGate.Decision.REJECTED_REENTRANT,
                gate.tryEnter(ScriptLifecycleGate.Operation.CLOSE));
        assertTrue(gate.isInLifecycle());
        gate.exit(ScriptLifecycleGate.Operation.RELOAD);
        // Once the lifecycle has ended, close enters normally.
        assertEquals(ScriptLifecycleGate.Decision.EXECUTED,
                gate.tryEnter(ScriptLifecycleGate.Operation.CLOSE));
        gate.exit(ScriptLifecycleGate.Operation.CLOSE);
    }

    @Test
    void reloadAfterCloseIsRejected() {
        ScriptLifecycleGate gate = new ScriptLifecycleGate();
        assertEquals(ScriptLifecycleGate.Decision.EXECUTED,
                gate.tryEnter(ScriptLifecycleGate.Operation.CLOSE));
        gate.exit(ScriptLifecycleGate.Operation.CLOSE);
        gate.markClosed();
        assertEquals(ScriptLifecycleGate.Decision.REJECTED_CLOSED,
                gate.tryEnter(ScriptLifecycleGate.Operation.RELOAD));
        assertEquals(ScriptLifecycleGate.Decision.REJECTED_CLOSED,
                gate.tryEnter(ScriptLifecycleGate.Operation.LOAD));
        // A repeated close after shutdown still passes idempotently (there is nothing left to clean up).
        assertEquals(ScriptLifecycleGate.Decision.EXECUTED,
                gate.tryEnter(ScriptLifecycleGate.Operation.CLOSE));
        gate.exit(ScriptLifecycleGate.Operation.CLOSE);
    }

    @Test
    void exitWithoutEnterThrows() {
        ScriptLifecycleGate gate = new ScriptLifecycleGate();
        assertThrows(IllegalStateException.class,
                () -> gate.exit(ScriptLifecycleGate.Operation.RELOAD));
    }

    @Test
    void activeFailedFlagOnlyClearedByExplicitRecovery() {
        ScriptLifecycleGate gate = new ScriptLifecycleGate();
        gate.markActiveFailed();
        assertTrue(gate.isActiveFailed());
        // closeRequested and closed do not implicitly clear the isolation-failure mark: only an explicit
        // lifecycle call clears it.
        gate.requestClose();
        assertTrue(gate.isActiveFailed());
        gate.clearActiveFailed();
        assertFalse(gate.isActiveFailed());
    }
}
