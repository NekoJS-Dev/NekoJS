package com.tkisor.nekojs.authoring;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AuthoringClientExecutorTest {
    @Test
    void epochIsStableWithoutServerAndUniqueAcrossRootOwners() {
        Harness first = new Harness();
        Harness replacement = new Harness();
        long epoch = first.client.epoch();
        assertTrue(epoch >= 0);
        assertEquals(epoch, first.client.epoch());
        assertNotEquals(epoch, replacement.client.epoch());
        long serverEpoch = AuthoringClientExecutor.nextEpoch();
        assertNotEquals(epoch, serverEpoch);
        assertNotEquals(replacement.client.epoch(), serverEpoch);
        first.active.set(false);
        assertEquals(-1, first.client.epoch());
        assertTrue(replacement.client.epoch() >= 0);
    }

    @Test
    void queuesForeignThreadAndCompletesCandidateOnlyOnOwner() {
        Harness harness = new Harness();
        AtomicInteger candidates = new AtomicInteger();
        AtomicBoolean completed = new AtomicBoolean();
        harness.client.execute(() -> {
            int result = harness.client.call(() -> {
                assertTrue(harness.owner.get());
                assertFalse(completed.get());
                return candidates.incrementAndGet();
            });
            assertEquals(1, result);
            completed.set(true);
        });
        assertEquals(0, candidates.get());
        assertFalse(completed.get());
        harness.drain();
        assertEquals(1, candidates.get());
        assertTrue(completed.get());
        assertTrue(harness.queue.isEmpty());
    }

    @Test
    void executesInlineOnOwnerAndRejectsDirectForeignCandidate() {
        Harness harness = new Harness();
        AtomicInteger candidates = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> harness.client.call(candidates::incrementAndGet));
        assertEquals(0, candidates.get());
        harness.owner.set(true);
        harness.client.execute(() -> {
            int result = harness.client.call(candidates::incrementAndGet);
            assertEquals(1, result);
        });
        assertEquals(1, candidates.get());
        assertTrue(harness.queue.isEmpty());
    }

    @Test
    void queuedCallbackStillRunsCoreLifetimeCheckAfterStopWithoutCandidateEffects() {
        Harness harness = new Harness();
        AtomicInteger candidates = new AtomicInteger();
        AtomicBoolean observedUnavailable = new AtomicBoolean();
        harness.client.execute(() -> {
            observedUnavailable.set(harness.client.epoch() < 0);
            assertThrows(IllegalStateException.class, () -> harness.client.call(candidates::incrementAndGet));
        });
        harness.active.set(false);
        harness.drain();
        assertTrue(observedUnavailable.get());
        assertEquals(0, candidates.get());
        assertThrows(IllegalStateException.class, () -> harness.client.execute(candidates::incrementAndGet));
        assertTrue(harness.queue.isEmpty());
    }

    @Test
    void stopDuringCandidateDoesNotReportSuccessfulCompletionOrPretendRollback() {
        Harness harness = new Harness();
        harness.owner.set(true);
        AtomicInteger effects = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> harness.client.call(() -> {
            effects.incrementAndGet();
            harness.active.set(false);
            return true;
        }));
        assertEquals(1, effects.get());
        assertEquals(-1, harness.client.epoch());
    }

    @Test
    void candidateFailurePropagatesWithoutPoisoningOwnerScheduler() {
        Harness harness = new Harness();
        harness.owner.set(true);
        IllegalArgumentException failure = new IllegalArgumentException("candidate failed");
        assertSame(failure, assertThrows(IllegalArgumentException.class,
                () -> harness.client.call(() -> { throw failure; })));
        assertEquals("next", harness.client.call(() -> "next"));
    }

    private static final class Harness {
        final ArrayDeque<Runnable> queue = new ArrayDeque<>();
        final AtomicBoolean owner = new AtomicBoolean();
        final AtomicBoolean active = new AtomicBoolean(true);
        final AuthoringClientExecutor client = new AuthoringClientExecutor(queue::add, owner::get, active::get);

        void drain() {
            owner.set(true);
            try {
                while (!queue.isEmpty()) queue.remove().run();
            } finally {
                owner.set(false);
            }
        }
    }
}
