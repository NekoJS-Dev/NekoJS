package com.tkisor.nekojs.core.lifecycle;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ReloadProgressTracker} 语义校验：进度按 currentStep/estimatedSteps 计算且 0.99 封顶、
 * finish 后 100% 停留 1.5s、活动快照更新后停留 5s、未 begin 的 step/update/finish 为 no-op、
 * 已有活动会话时嵌套 begin 返回 false 并保留外层会话。
 */
class ReloadProgressTrackerTest {

    /** 可推进的假时钟（毫秒）。 */
    private static final class FakeClock implements LongSupplier {
        final AtomicLong millis = new AtomicLong(1_000L);

        void advanceMs(long ms) {
            millis.addAndGet(ms);
        }

        @Override
        public long getAsLong() {
            return millis.get();
        }
    }

    private FakeClock clock;

    @BeforeEach
    void setUp() {
        ReloadProgressTracker.resetForTest();
        clock = new FakeClock();
        ReloadProgressTracker.setClock(clock);
    }

    @AfterEach
    void tearDown() {
        ReloadProgressTracker.resetForTest();
        ReloadProgressTracker.resetClock();
    }

    @Test
    void beginStartsVisibleActiveSessionAtZero() {
        assertTrue(ReloadProgressTracker.begin("client", 4));
        ReloadProgressTracker.Snapshot snapshot = ReloadProgressTracker.snapshot();
        assertTrue(snapshot.active());
        assertTrue(snapshot.visibleAt(clock.getAsLong()));
        assertEquals(0.0f, snapshot.progress());
        assertEquals("client", snapshot.scriptType());
    }

    @Test
    void stepAdvancesProgressAsFractionOfEstimate() {
        ReloadProgressTracker.begin("server", 4);
        ReloadProgressTracker.step("server", "discovered");
        assertEquals(0.25f, ReloadProgressTracker.snapshot().progress(), 1e-6f);
        assertEquals("discovered", ReloadProgressTracker.snapshot().message());
        ReloadProgressTracker.step("server", "executed");
        assertEquals(0.5f, ReloadProgressTracker.snapshot().progress(), 1e-6f);
    }

    @Test
    void progressCappedAtPointNineNineWhileActive() {
        ReloadProgressTracker.begin("client", 2);
        ReloadProgressTracker.step("client", "one");   // 0.5
        ReloadProgressTracker.step("client", "two");   // 1.0 -> capped
        ReloadProgressTracker.step("client", "three"); // beyond estimate -> stays capped
        ReloadProgressTracker.Snapshot snapshot = ReloadProgressTracker.snapshot();
        assertEquals(ReloadProgressTracker.ACTIVE_PROGRESS_CAP, snapshot.progress(), 1e-6f);
        assertTrue(snapshot.active(), "cap 期间会话仍为 active，只有 finish 才到 100%");
    }

    @Test
    void updateChangesMessageWithoutAdvancingProgress() {
        ReloadProgressTracker.begin("startup", 3);
        ReloadProgressTracker.step("startup", "step1");
        ReloadProgressTracker.update("startup", "still working");
        ReloadProgressTracker.Snapshot snapshot = ReloadProgressTracker.snapshot();
        assertEquals("still working", snapshot.message());
        assertEquals(1f / 3f, snapshot.progress(), 1e-6f, "update 不应推进步数");
    }

    @Test
    void finishShowsFullProgressBrieflyThenHides() {
        ReloadProgressTracker.begin("client", 2);
        ReloadProgressTracker.step("client", "loaded");
        ReloadProgressTracker.finish("client", true);

        ReloadProgressTracker.Snapshot snapshot = ReloadProgressTracker.snapshot();
        assertFalse(snapshot.active());
        assertEquals(1.0f, snapshot.progress());
        assertTrue(snapshot.message().contains("ready"));
        assertTrue(snapshot.visibleAt(clock.getAsLong()), "finish 后 1.5s 内仍可见");

        clock.advanceMs(ReloadProgressTracker.FINISH_LINGER_MILLIS);
        assertTrue(snapshot.visibleAt(clock.getAsLong()), "恰好到达窗口边界仍可见（<=）");
        clock.advanceMs(1);
        assertFalse(snapshot.visibleAt(clock.getAsLong()), "超过 finish 窗口后隐藏");
    }

    @Test
    void finishFailureMessageMarksFailure() {
        ReloadProgressTracker.begin("server", 1);
        ReloadProgressTracker.finish("server", false);
        assertTrue(ReloadProgressTracker.snapshot().message().contains("failed"));
        assertEquals(1.0f, ReloadProgressTracker.snapshot().progress());
    }

    @Test
    void activeSnapshotLingersFiveSecondsAfterLastUpdate() {
        ReloadProgressTracker.begin("client", 10);
        ReloadProgressTracker.step("client", "slow step");
        ReloadProgressTracker.Snapshot snapshot = ReloadProgressTracker.snapshot();

        clock.advanceMs(ReloadProgressTracker.UPDATE_LINGER_MILLIS);
        assertTrue(snapshot.visibleAt(clock.getAsLong()), "活动快照更新后 5s 内可见");
        clock.advanceMs(1);
        assertTrue(snapshot.visibleAt(clock.getAsLong()), "active 快照无条件可见（窗口只防旧快照闪烁）");
    }

    @Test
    void stepWithoutBeginIsNoOp() {
        ReloadProgressTracker.step("unknown", "orphan");
        ReloadProgressTracker.update("unknown", "orphan");
        ReloadProgressTracker.finish("unknown", true);
        ReloadProgressTracker.Snapshot snapshot = ReloadProgressTracker.snapshot();
        assertFalse(snapshot.active());
        assertFalse(snapshot.visibleAt(clock.getAsLong()), "无会话时不应产生可见快照");
        assertEquals(0.0f, snapshot.progress());
    }

    @Test
    void nullAndUnknownTypesAreTolerated() {
        assertFalse(ReloadProgressTracker.begin(null, 3));
        ReloadProgressTracker.step(null, "ignored");
        ReloadProgressTracker.update(null, "ignored");
        ReloadProgressTracker.finish(null, true);
        assertFalse(ReloadProgressTracker.snapshot().visibleAt(clock.getAsLong()));
    }

    @Test
    void nestedBeginKeepsOuterSessionAndReportsFalse() {
        assertTrue(ReloadProgressTracker.begin("client", 5));
        ReloadProgressTracker.step("client", "outer step 1");
        assertFalse(ReloadProgressTracker.begin("client", 2), "嵌套 begin 不应重置外层会话");
        ReloadProgressTracker.step("client", "outer step 2");
        assertEquals(0.4f, ReloadProgressTracker.snapshot().progress(), 1e-6f, "仍按外层 estimatedSteps=5 计算进度");
    }

    @Test
    void finishClosesSessionSoNextBeginStartsFresh() {
        assertTrue(ReloadProgressTracker.begin("server", 2));
        ReloadProgressTracker.finish("server", true);
        assertTrue(ReloadProgressTracker.begin("server", 3), "finish 后可重新 begin");
        assertEquals(0.0f, ReloadProgressTracker.snapshot().progress());
        assertTrue(ReloadProgressTracker.snapshot().active());
    }

    @Test
    void sessionsAreIndependentPerType() {
        assertTrue(ReloadProgressTracker.begin("server", 2));
        assertTrue(ReloadProgressTracker.begin("client", 4));
        ReloadProgressTracker.step("server", "s1");
        ReloadProgressTracker.step("server", "s2");
        ReloadProgressTracker.step("client", "c1");
        ReloadProgressTracker.Snapshot snapshot = ReloadProgressTracker.snapshot();
        assertEquals("client", snapshot.scriptType(), "快照反映最后一次更新的类型");
        assertEquals(0.25f, snapshot.progress(), 1e-6f);
    }

    @Test
    void hideAllClearsEverything() {
        ReloadProgressTracker.begin("client", 2);
        ReloadProgressTracker.hideAll();
        ReloadProgressTracker.Snapshot snapshot = ReloadProgressTracker.snapshot();
        assertFalse(snapshot.active());
        assertFalse(snapshot.visibleAt(clock.getAsLong()));
        // hideAll 之后 step 也不应复活会话
        ReloadProgressTracker.step("client", "dead");
        assertFalse(ReloadProgressTracker.snapshot().active());
    }
}
