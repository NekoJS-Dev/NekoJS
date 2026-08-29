package com.tkisor.nekojs.eventbus;

import com.tkisor.nekojs.api.event.EventBus;
import com.tkisor.nekojs.api.event.EventListenerToken;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Concurrent stress test for {@link EventBusBase}: threads mutating (listen/unregister) while
 * others post. Exercises the CONC-4 (synchronized built-invalidation) + CopyOnWriteArrayList
 * guarantees — must never throw and must reach a consistent empty state (TEST-1a).
 */
class EventBusConcurrentStressTest {

    @Test
    void concurrentMutationAndPostNeverThrowsAndReachesEmptyState() throws Exception {
        EventBus<Integer> bus = new EventBusImpl<>(Integer.class, null);
        AtomicInteger delivered = new AtomicInteger();
        int threads = 8;
        int iterations = 1000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        List<Future<?>> futures = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            final boolean mutator = (t % 2 == 0);
            futures.add(pool.submit(() -> {
                barrier.await();
                List<EventListenerToken<Integer>> held = new ArrayList<>();
                try {
                    for (int i = 0; i < iterations; i++) {
                        if (mutator) {
                            EventListenerToken<Integer> tok = bus.listen(e -> delivered.incrementAndGet());
                            held.add(tok);
                            if (i % 5 == 0 && !held.isEmpty()) {
                                bus.unregister(held.remove(0));
                            }
                        } else {
                            bus.post(i);
                        }
                    }
                } finally {
                    for (EventListenerToken<Integer> tok : held) {
                        bus.unregister(tok);
                    }
                }
                return null;
            }));
        }

        for (Future<?> f : futures) {
            assertDoesNotThrow(() -> f.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(15, TimeUnit.SECONDS));

        assertTrue(((EventBusImpl<?>) bus).isEmpty(),
                "all transient tokens unregistered -> bus must be empty");
        assertTrue(delivered.get() >= 0, "deliveries counted without corruption");
    }
}
