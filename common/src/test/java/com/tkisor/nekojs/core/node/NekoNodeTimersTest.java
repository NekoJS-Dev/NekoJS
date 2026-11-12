package com.tkisor.nekojs.core.node;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.DefaultErrorTracker;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Verifies the pending-timer capacity independently of script execution budgets.
 * Each registration retains a callback and scheduling handle, so overflow must fail
 * with {@link IllegalStateException} and clearing timers must release the capacity.
 *
 * <p>No Graal Context is needed: capacity is checked before a null callback is used,
 * and the delayed timers cannot fire during this test. The fixture discovers the cap
 * from registration failure rather than hard-coding its value.
 */
class NekoNodeTimersTest {

    /** 远超任何合理上限的安全上界，防止回归（上限失效）时死循环。 */
    private static final int SAFETY_BOUND = 100_000;

    private NekoNodeTimers timers;

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @BeforeEach
    void newTimers() {
        timers = new NekoNodeTimers(ScriptType.SERVER, new DefaultErrorTracker(NekoJSPaths.get(), SandboxConfig.defaultConfig()));
    }

    @AfterEach
    void closeTimers() {
        timers.close();
    }

    @Test
    void setTimeoutRegistrationIsBounded() {
        int registered = registerUntilRejected(true);
        assertTrue(registered > 0, "首次注册必须成功");
        assertTrue(registered < SAFETY_BOUND, "setTimeout 注册必须有上限（否则 OOM）");
    }

    @Test
    void clearIntervalRegistrationIsBounded() {
        int registered = registerUntilRejected(false);
        assertTrue(registered > 0, "首次注册必须成功");
        assertTrue(registered < SAFETY_BOUND, "setInterval 注册必须有上限（否则 OOM）");
    }

    @Test
    void clearTimeoutReleasesCapacity() {
        int lastId = -1;
        int registered = 0;
        for (int i = 0; i < SAFETY_BOUND; i++) {
            try {
                lastId = timers.setTimeout(null, 60_000L);
                registered++;
            } catch (IllegalStateException e) {
                break;
            }
        }
        assertTrue(registered > 0 && registered < SAFETY_BOUND, "注册必须先到达上限");
        timers.clearTimeout(lastId);
        assertDoesNotThrow(() -> timers.setTimeout(null, 60_000L), "clear 之后名额释放，应可继续注册");
    }

    @Test
    void closeRejectsLateRegistrationAndClearsPendingCallbacks() {
        timers.setInterval(null, 1L);
        timers.close();
        assertFalse(timers.hasPendingCallbacks());
        assertTrue(assertThrows(IllegalStateException.class, () -> timers.setImmediate(null)).getMessage().contains("NEKO-1017"));
        assertThrows(IllegalStateException.class, () -> timers.setInterval(null, 1L));
        assertDoesNotThrow(timers::flushReadyCallbacks);
        assertDoesNotThrow(timers::close);
    }

    @Test
    void closeReportsAnUnterminatedWorkerAndCanRetryAfterItExits() throws Exception {
        var field = NekoNodeTimers.class.getDeclaredField("scheduler");
        field.setAccessible(true);
        var scheduler = (java.util.concurrent.ScheduledExecutorService) field.get(timers);
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        scheduler.execute(() -> {
            entered.countDown();
            while (release.getCount() > 0) {
                try { release.await(); } catch (InterruptedException ignored) { }
            }
        });
        try {
            assertTrue(entered.await(3, java.util.concurrent.TimeUnit.SECONDS));
            assertTrue(assertThrows(IllegalStateException.class, timers::close).getMessage().contains("NEKO-1017"));
            assertFalse(timers.hasPendingCallbacks());
        } finally {
            release.countDown();
            assertTrue(scheduler.awaitTermination(3, java.util.concurrent.TimeUnit.SECONDS));
        }
        assertDoesNotThrow(timers::close);
    }

    /** 循环注册直到首个 {@link IllegalStateException}，返回成功注册数（即实际上限）。 */
    private int registerUntilRejected(boolean oneShot) {
        int registered = 0;
        for (int i = 0; i < SAFETY_BOUND; i++) {
            try {
                if (oneShot) {
                    timers.setTimeout(null, 60_000L);
                } else {
                    timers.setInterval(null, 60_000L);
                }
                registered++;
            } catch (IllegalStateException e) {
                return registered;
            }
        }
        return registered;
    }
}
