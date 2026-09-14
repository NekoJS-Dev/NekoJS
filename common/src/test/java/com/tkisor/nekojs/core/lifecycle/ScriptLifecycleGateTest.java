package com.tkisor.nekojs.core.lifecycle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 票 07 owner-thread 调度门的纯单元契约：串行进入、reload 不重入、LOAD 在 RELOAD
 * 内嵌套放行（STARTUP reset+load）、回调内请求拒绝、close 优先与关闭后拒绝、
 * 同线程 close 嵌套拒绝、隔离失败标记只由显式恢复清除。
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
        // RELOAD 内的一层 LOAD 是 STARTUP reset+load 的既有嵌套形状（见
        // loadNestedInReloadIsAllowedForStartupResetPath）；此处只证 RELOAD 不重入。
        gate.exit(ScriptLifecycleGate.Operation.RELOAD);
    }

    @Test
    void loadNestedInReloadIsAllowedForStartupResetPath() {
        ScriptLifecycleGate gate = new ScriptLifecycleGate();
        assertEquals(ScriptLifecycleGate.Decision.EXECUTED,
                gate.tryEnter(ScriptLifecycleGate.Operation.RELOAD));
        assertEquals(ScriptLifecycleGate.Decision.EXECUTED,
                gate.tryEnter(ScriptLifecycleGate.Operation.LOAD));
        // RELOAD 内的 LOAD 内再 LOAD 仍拒绝：只有一层既有嵌套被放行
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
        // 仍在外层回调内：继续拒绝
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
        // lifecycle 体内的同线程 close 只留下抢占标志，不内联拆除
        assertEquals(ScriptLifecycleGate.Decision.REJECTED_REENTRANT,
                gate.tryEnter(ScriptLifecycleGate.Operation.CLOSE));
        assertTrue(gate.isInLifecycle());
        gate.exit(ScriptLifecycleGate.Operation.RELOAD);
        // lifecycle 结束后 close 正常进入
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
        // 已关闭后的重复 close 保持幂等放行（空清理）
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
        // closeRequested/closed 不隐式清除隔离失败：恢复入口只有显式 lifecycle
        gate.requestClose();
        assertTrue(gate.isActiveFailed());
        gate.clearActiveFailed();
        assertFalse(gate.isActiveFailed());
    }
}
