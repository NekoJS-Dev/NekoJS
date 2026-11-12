package com.tkisor.nekojs.script;

import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Engine;
import graal.graalvm.polyglot.HostAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises host-owned cancellation and context serialization without production execution limits. */
@Timeout(30)
class ScriptContextCancellationTest {
    public static final class ProbeSignal {
        final CountDownLatch entered = new CountDownLatch(1);
        public void enter() { entered.countDown(); }
    }

    @Test
    void concurrentEvalOnSameContextIsRejectedByGraal() throws Exception {
        try (Engine engine = Engine.newBuilder().build()) {
            ProbeSignal signal = new ProbeSignal();
            Context context = newContext(engine, signal);
            Thread spinner = startSpinner(context, new AtomicReference<>());
            try {
                assertTrue(signal.entered.await(10, TimeUnit.SECONDS));
                assertThrows(Throwable.class, () -> context.eval("js", "1 + 1"),
                        "Concurrent context entry must remain rejected");
            } finally {
                stopSpinner(context, spinner);
            }
        }
    }

    @Test
    void interruptFromOtherThreadAbortsRunningEval() throws Exception {
        try (Engine engine = Engine.newBuilder().build()) {
            ProbeSignal signal = new ProbeSignal();
            Context context = newContext(engine, signal);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread spinner = startSpinner(context, failure);
            try {
                assertTrue(signal.entered.await(10, TimeUnit.SECONDS));
                context.interrupt(Duration.ofSeconds(5));
                spinner.join(TimeUnit.SECONDS.toMillis(5));
                assertFalse(spinner.isAlive(), "Explicit host interruption must stop guest evaluation");
                assertNotNull(failure.get());
            } finally {
                stopSpinner(context, spinner);
            }
        }
    }

    @Test
    void externalCancellationIsClassifiedThroughWrappedCauses() throws Exception {
        try (Engine engine = Engine.newBuilder().build()) {
            ProbeSignal signal = new ProbeSignal();
            Context context = newContext(engine, signal);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread spinner = startSpinner(context, failure);
            try {
                assertTrue(signal.entered.await(10, TimeUnit.SECONDS));
                context.close(true);
                spinner.join(TimeUnit.SECONDS.toMillis(5));
                assertFalse(spinner.isAlive());
                assertNotNull(failure.get());
                assertTrue(ScriptExecutor.isContextTerminated(new RuntimeException("Wrapped host cancellation", failure.get())));
                assertFalse(ScriptExecutor.isContextTerminated(new IllegalStateException("Ordinary script failure")));
                assertFalse(ScriptExecutor.isContextTerminated(null));
            } finally {
                stopSpinner(context, spinner);
            }
        }
    }

    private static Context newContext(Engine engine, ProbeSignal signal) {
        Context context = Context.newBuilder("js").engine(engine).allowHostAccess(HostAccess.ALL).build();
        context.getBindings("js").putMember("__probeEnter", signal);
        return context;
    }

    private static Thread startSpinner(Context context, AtomicReference<Throwable> failure) {
        Thread spinner = new Thread(() -> {
            try {
                context.eval("js", "__probeEnter.enter(); const end = Date.now() + 10_000; "
                        + "for (let spin = 0; spin < 1_000_000_000 && Date.now() < end; spin++) {}");
            } catch (Throwable error) {
                failure.set(error);
            }
        }, "context-cancellation-probe");
        spinner.setDaemon(true);
        spinner.start();
        return spinner;
    }

    private static void stopSpinner(Context context, Thread spinner) throws InterruptedException {
        // This bounded harness belongs to the test, not to script execution policy.
        try {
            if (spinner.isAlive()) context.interrupt(Duration.ofSeconds(5));
        } catch (Exception ignored) {
            // Forced host close below is also required after a failed interrupt.
        } finally {
            context.close(true);
        }
        spinner.join(TimeUnit.SECONDS.toMillis(5));
        assertFalse(spinner.isAlive(), "The cancellation fixture must not leave a guest thread running");
    }
}
