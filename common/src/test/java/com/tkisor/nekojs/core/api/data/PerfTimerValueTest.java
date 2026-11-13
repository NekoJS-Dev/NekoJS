package com.tkisor.nekojs.core.api.data;

import com.tkisor.nekojs.api.data.PerfTimerValue;
import com.tkisor.nekojs.api.data.PerfTimerValue.MarkReport;
import com.tkisor.nekojs.api.data.PerfTimerValue.PerfTimerReport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the pure JDK semantics of {@link PerfTimerValue}; no Graal Context is needed.
 */
class PerfTimerValueTest {

    @Test
    void startCapturesLabelAndBaseline() {
        PerfTimerValue t = PerfTimerValue.start("phase");
        assertEquals("phase", t.label());
        assertFalse(t.ended());
        assertTrue(t.marks().isEmpty());
        assertTrue(t.elapsedMillis() >= 0.0);
    }

    @Test
    void startWithNullLabelProducesNullLabel() {
        PerfTimerValue t = PerfTimerValue.start(null);
        assertNull(t.label());
    }

    @Test
    void markIsImmutableAndChainable() throws InterruptedException {
        PerfTimerValue t0 = PerfTimerValue.start("load");
        Thread.sleep(2);
        PerfTimerValue t1 = t0.mark("parse");
        // t0 is immutable: mark() left it unchanged
        assertEquals(0, t0.marks().size());
        assertEquals(1, t1.marks().size());
        assertEquals("parse", t1.marks().getFirst().label());
        Thread.sleep(2);
        PerfTimerValue t2 = t1.mark("compile");
        assertEquals(2, t2.marks().size());
        assertEquals("compile", t2.marks().get(1).label());
    }

    @Test
    void endFreezesElapsedBaseline() throws InterruptedException {
        PerfTimerValue t = PerfTimerValue.start("x").end();
        double first = t.elapsedMillis();
        Thread.sleep(3);
        double second = t.elapsedMillis();
        // elapsedMillis() stops growing after end; it is measured from the last mark, or from start
        // when there is no mark
        assertEquals(first, second, 0.001);
    }

    @Test
    void markAfterEndIsNoOp() {
        PerfTimerValue ended = PerfTimerValue.start("x").end();
        PerfTimerValue after = ended.mark("late");
        assertSame(ended, after);
    }

    @Test
    void reportContainsMarksWithIncreasingAtAndSincePrev() throws InterruptedException {
        PerfTimerValue t = PerfTimerValue.start("load");
        Thread.sleep(2);
        t = t.mark("a");
        Thread.sleep(2);
        t = t.mark("b");
        PerfTimerReport r = t.report();
        assertEquals("load", r.label());
        assertEquals(2, r.marks().size());
        List<MarkReport> marks = r.marks();
        assertEquals("a", marks.get(0).label());
        assertEquals("b", marks.get(1).label());
        assertTrue(marks.get(0).at() <= marks.get(1).at());
        assertTrue(marks.get(0).sincePrev() >= 0.0);
        assertTrue(marks.get(1).sincePrev() >= 0.0);
        assertTrue(r.total() >= 0.0);
    }

    @Test
    void elapsedMillisMonotonicNonDecreasing() throws InterruptedException {
        PerfTimerValue t = PerfTimerValue.start("m");
        double a = t.elapsedMillis();
        Thread.sleep(2);
        double b = t.elapsedMillis();
        assertTrue(b >= a, "elapsedMillis must be non-decreasing");
    }
}
