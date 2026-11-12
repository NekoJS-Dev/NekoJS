package com.tkisor.nekojs.script;

import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.node.NekoNodeRuntime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/** Keeps future polling, interruption and cause propagation independent of execution-limit policy. */
@Timeout(10)
class ScriptExecutorEvaluationTest {
    @Test
    void completedEvaluationReturnsNormally() throws Exception {
        waitForEvaluation(CompletableFuture.completedFuture(null));
    }

    @Test
    void exceptionalEvaluationPreservesItsOriginalCause() {
        IllegalStateException failure = new IllegalStateException("Native evaluation failed");
        InvocationTargetException observed = assertThrows(InvocationTargetException.class,
                () -> waitForEvaluation(CompletableFuture.failedFuture(failure)));
        assertSame(failure, observed.getCause());
    }

    @Test
    void interruptedPendingEvaluationPreservesTheThreadFlag() {
        CompletableFuture<Void> pending = new CompletableFuture<>();
        Thread.currentThread().interrupt();
        try {
            InvocationTargetException observed = assertThrows(InvocationTargetException.class,
                    () -> waitForEvaluation(pending));
            assertInstanceOf(InterruptedException.class, observed.getCause());
            assertTrue(Thread.currentThread().isInterrupted());
            assertFalse(pending.isDone(), "Host interruption must not fabricate an evaluation result");
        } finally {
            Thread.interrupted();
        }
    }

    private static void waitForEvaluation(CompletableFuture<?> evaluation) throws Exception {
        ScriptExecutor executor = new ScriptExecutor(null, null, SandboxConfig.defaultConfig(), context -> {});
        Method method = ScriptExecutor.class.getDeclaredMethod("waitForEvaluation", CompletableFuture.class, NekoNodeRuntime.class);
        method.setAccessible(true);
        method.invoke(executor, evaluation, null);
    }
}
