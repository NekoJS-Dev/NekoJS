package com.tkisor.nekojs.core.lifecycle;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * {@link ResourceTracker} 的关闭语义与并发安全。
 *
 * <p>修复契约：track/cleanup/size/isEmpty 全部 synchronized；close 在锁内只做
 * 幂等标记 + 快照 + 清空，用户清理动作在锁外按 LIFO 执行，单条失败继续执行并
 * 最终重抛首个 Throwable；close 之后 track/cleanup（含 null 输入）必须抛
 * {@link IllegalStateException}，而不是登记一个永远不会执行的清理动作。
 */
class ResourceTrackerTest {

    @Test
    void closeRunsCleanupsInLifoOrder() {
        ResourceTracker tracker = new ResourceTracker();
        List<String> order = new ArrayList<>();
        tracker.cleanup("first", () -> order.add("first"));
        tracker.cleanup("second", () -> order.add("second"));
        tracker.cleanup("third", () -> order.add("third"));

        tracker.close();

        assertEquals(List.of("third", "second", "first"), order);
    }

    @Test
    void closeContinuesAfterFailureAndRethrowsFirstThrowable() {
        ResourceTracker tracker = new ResourceTracker();
        List<String> order = new ArrayList<>();
        tracker.cleanup("a", () -> {
            order.add("a");
            throw new RuntimeException("boom-a");
        });
        tracker.cleanup("b", () -> {
            order.add("b");
            throw new RuntimeException("boom-b");
        });
        tracker.cleanup("c", () -> order.add("c"));

        RuntimeException ex = assertThrows(RuntimeException.class, tracker::close);

        assertEquals("boom-b", ex.getMessage(), "LIFO order means b fails first");
        assertEquals(1, ex.getSuppressed().length, "a's failure must be attached as suppressed");
        assertEquals("boom-a", ex.getSuppressed()[0].getMessage());
        assertEquals(List.of("c", "b", "a"), order,
                "every cleanup must run even when earlier cleanups fail");
    }

    @Test
    void closeIsIdempotent() {
        ResourceTracker tracker = new ResourceTracker();
        AtomicInteger calls = new AtomicInteger();
        tracker.cleanup("once", calls::incrementAndGet);

        tracker.close();
        tracker.close();

        assertEquals(1, calls.get(), "second close must be a no-op");
    }

    @Test
    void trackAfterCloseThrowsIllegalStateException() {
        ResourceTracker tracker = new ResourceTracker();
        tracker.close();

        assertThrows(IllegalStateException.class,
                () -> tracker.track("late", (AutoCloseable) () -> { }));
    }

    @Test
    void cleanupAfterCloseThrowsIllegalStateException() {
        ResourceTracker tracker = new ResourceTracker();
        tracker.close();

        assertThrows(IllegalStateException.class,
                () -> tracker.cleanup("late", () -> { }));
    }

    @Test
    void trackNullAfterCloseThrowsIllegalStateException() {
        ResourceTracker tracker = new ResourceTracker();
        tracker.close();

        assertThrows(IllegalStateException.class,
                () -> tracker.track("late", (AutoCloseable) null),
                "post-close null input must be rejected before the null shortcut");
    }

    @Test
    void cleanupNullAfterCloseThrowsIllegalStateException() {
        ResourceTracker tracker = new ResourceTracker();
        tracker.close();

        assertThrows(IllegalStateException.class,
                () -> tracker.cleanup("late", (Runnable) null),
                "post-close null input must be rejected before the null shortcut");
    }

    /**
     * close 必须只在锁内完成「标记 + 快照 + 清空」，然后释放锁再运行用户清理动作。
     * 若 close 全程持锁，本测试中 blocked cleanup 等待 release 期间，size() 会阻塞。
     */
    @Test
    void closeReleasesMonitorBeforeRunningCleanups() throws Exception {
        ResourceTracker tracker = new ResourceTracker();
        CountDownLatch cleanupStarted = new CountDownLatch(1);
        CountDownLatch releaseCleanup = new CountDownLatch(1);
        tracker.cleanup("blocked", () -> {
            cleanupStarted.countDown();
            try {
                releaseCleanup.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> closeFuture = pool.submit(() -> {
                tracker.close();
                return null;
            });
            assertTrue(cleanupStarted.await(10, TimeUnit.SECONDS),
                    "cleanup should start and block before we probe size()");

            Future<Integer> sizeFuture = pool.submit(tracker::size);
            Integer size;
            try {
                size = sizeFuture.get(2, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                fail("size() blocked: close must release the monitor before running user cleanups");
                return;
            }
            assertEquals(0, size,
                    "snapshot/clear happen under the lock, so size() observes 0 while cleanups run");

            releaseCleanup.countDown();
            assertDoesNotThrow(() -> closeFuture.get(10, TimeUnit.SECONDS));
        } finally {
            releaseCleanup.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void sizeAndIsEmptyTrackEntries() {
        ResourceTracker tracker = new ResourceTracker();
        assertTrue(tracker.isEmpty());
        assertEquals(0, tracker.size());

        tracker.track("a", (AutoCloseable) () -> { });
        assertEquals(1, tracker.size());
        tracker.cleanup("b", () -> { });
        assertEquals(2, tracker.size());

        tracker.close();
        assertTrue(tracker.isEmpty());
        assertEquals(0, tracker.size());
    }
}
