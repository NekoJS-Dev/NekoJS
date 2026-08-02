package com.tkisor.nekojs.core.api.facade;

import com.tkisor.nekojs.api.data.PerfTimerValue;
import com.tkisor.nekojs.api.facade.PerfStat;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link DefaultPerformanceFacade} 测试。time/bench 用真实 Graal Context 构造 JS 函数 Value。
 */
class DefaultPerformanceFacadeTest {

    private final DefaultPerformanceFacade facade = new DefaultPerformanceFacade();

    @Test
    void nowReturnsMonotonicIncreasingMillis() {
        double a = facade.now();
        double b = facade.now();
        assertTrue(b >= a, "now() must be monotonic non-decreasing");
        assertTrue(a > 0.0);
    }

    @Test
    void timeMeasuresExecutedFunction() {
        try (Context ctx = Context.newBuilder("js").allowAllAccess(true).build()) {
            Value fn = ctx.eval("js", "() => { let s = 0; for (let i = 0; i < 1000; i++) s += i; return s; }");
            double ms = facade.time(fn);
            assertTrue(ms >= 0.0, "time() should report a non-negative duration");
        }
    }

    @Test
    void benchRunsRequestedTimesAndReportsStats() {
        try (Context ctx = Context.newBuilder("js").allowAllAccess(true).build()) {
            Value fn = ctx.eval("js", "() => { let s = 0; for (let i = 0; i < 500; i++) s += i; }");
            PerfStat stat = facade.bench(fn, 50);
            assertEquals(50, stat.runs());
            assertTrue(stat.total() >= 0.0);
            assertTrue(stat.min() <= stat.max(), "min must not exceed max");
            assertTrue(stat.mean() >= 0.0);
            // mean within [min, max]
            assertTrue(stat.mean() >= stat.min() - 1e-9 && stat.mean() <= stat.max() + 1e-9);
        }
    }

    @Test
    void benchWithZeroRunsReturnsEmptyStat() {
        try (Context ctx = Context.newBuilder("js").allowAllAccess(true).build()) {
            Value fn = ctx.eval("js", "() => {}");
            PerfStat stat = facade.bench(fn, 0);
            assertEquals(0, stat.runs());
            assertEquals(0.0, stat.total());
        }
    }

    @Test
    void timeRejectsNonExecutable() {
        assertThrows(IllegalArgumentException.class, () -> facade.time("not a function"));
        assertThrows(NullPointerException.class, () -> facade.time(null));
    }

    @Test
    void benchRejectsNonExecutable() {
        assertThrows(IllegalArgumentException.class, () -> facade.bench(42, 10));
    }

    @Test
    void startReturnsPerfTimerWithLabel() {
        PerfTimerValue t = facade.start("phase");
        assertNotNull(t);
        assertEquals("phase", t.label());
        assertFalse(t.ended());
    }

    @Test
    void startWithNullLabel() {
        PerfTimerValue t = facade.start(null);
        assertNull(t.label());
    }
}
