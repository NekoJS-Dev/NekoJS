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

/**
 * {@link NekoNodeTimers} 注册数上限（MAX_SCHEDULED_TIMERS）回归测试：
 * {@code while(true){setInterval(()=>{},1000)}} 会在语句上限触发前向 tasks 无限
 * 塞入条目（每个持有 JS 回调与调度句柄）导致 OOM，注册路径必须在超限时抛
 * {@link IllegalStateException}（与 STARTUP 拒绝延迟 timer 同款机制）。
 *
 * <p>无需 Graal Context：上限检查先于回调使用，callback 传 null 即可
 * （{@code recordScriptId} 对 null 直接返回，60s 延迟保证测试期内不触发）。
 * 不硬编码上限数值——注册到首个异常即得上限，clear 后名额释放即可续注。
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
