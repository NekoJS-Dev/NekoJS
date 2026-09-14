package com.tkisor.nekojs.core.lifecycle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 票 07 owner-thread 调度门的纯单元契约：串行进入、reload 不重入、LOAD 在 RELOAD
 * 内嵌套放行（STARTUP reset+load）、回调内请求拒绝、close 优先与关闭后拒绝。
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
        gate.exit(ScriptLifecycleGate.Operation.RELOAD);
    }

    @Test
    void loadNestedInReloadIsAllowedForStartupResetPath() {
        ScriptLifecycleGate gate = new ScriptLifecycleGate();
        assertEquals(ScriptLifecycleGate.Decision.EXECUTED,
                gate.tryEnter(ScriptLifecycleGate.Operation.RELOAD));
        assertEquals(ScriptLifecycleGate.Decision.EXECUTED,
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
        } finally {
            ScriptLifecycleGate.exitCallback();
        }
        assertFalse(gate.isInLifecycle());
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
    }

    @Test
    void activeFailedFlagOnlyClearedByExplicitRecovery() {
        ScriptLifecycleGate gate = new ScriptLifecycleGate();
        gate.markActiveFailed();
        assertTrue(gate.isActiveFailed());
        gate.clearActiveFailed();
        assertFalse(gate.isActiveFailed());
    }
}
