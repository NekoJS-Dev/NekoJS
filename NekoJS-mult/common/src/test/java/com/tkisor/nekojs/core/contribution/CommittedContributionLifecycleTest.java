package com.tkisor.nekojs.core.contribution;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommittedContributionLifecycleTest {
    @Test
    void failedSealingRetainsThePreviouslyCommittedPlan() {
        List<String> candidate = new ArrayList<>();
        List<String> applied = new ArrayList<>();
        CommittedContributionLifecycle<List<String>, String, String> lifecycle =
                new CommittedContributionLifecycle<>(
                        ArrayList::new,
                        values -> {
                            if (values.contains("bad")) {
                                throw new IllegalArgumentException("bad candidate");
                            }
                            return String.join(",", values);
                        },
                        (plan, epoch) -> applied.add(plan + "@" + epoch));

        lifecycle.begin();
        lifecycle.collect().add("good");
        assertEquals("good", lifecycle.seal());
        lifecycle.registryReady("epoch-1");

        lifecycle.begin();
        candidate = lifecycle.collect();
        candidate.add("bad");
        assertThrows(IllegalArgumentException.class, lifecycle::seal);

        assertEquals("good", lifecycle.committedPlan());
        assertTrue(lifecycle.isCollecting());
        assertEquals(List.of("good@epoch-1"), applied);
    }

    @Test
    void adapterIsCalledOncePerRevisionAndEpochAndResetOnStop() {
        List<String> applied = new ArrayList<>();
        CommittedContributionLifecycle<List<String>, String, String> lifecycle =
                new CommittedContributionLifecycle<>(
                        ArrayList::new,
                        values -> String.join(",", values),
                        (plan, epoch) -> applied.add(plan + "@" + epoch));

        lifecycle.begin();
        lifecycle.collect().add("one");
        String first = lifecycle.seal();
        assertSame(first, lifecycle.seal());
        assertTrue(lifecycle.registryReady("epoch-1"));
        assertTrue(!lifecycle.registryReady("epoch-1"));

        lifecycle.begin();
        lifecycle.collect().add("two");
        lifecycle.seal();
        assertEquals(List.of("one@epoch-1"), applied);
        assertTrue(lifecycle.registryReady("epoch-1"));
        assertEquals(List.of("one@epoch-1", "two@epoch-1"), applied);

        lifecycle.stop();
        assertEquals(0L, lifecycle.committedRevision());
        assertEquals(null, lifecycle.committedPlan());
    }
}
