package com.tkisor.nekojs.core.pack;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScriptPackResourceStateTest {
    @Test void failedReloadRemainsPendingUntilActualSuccess() {
        var state = new ScriptPackResourceState();
        Object repository = new Object();
        assertFalse(state.select(repository, ""));
        assertTrue(state.select(repository, "pack-a"));
        var failed = state.capture();
        assertTrue(state.select(repository, "pack-a"));
        assertTrue(state.applied(failed));
        assertFalse(state.select(repository, "pack-a"));
    }

    @Test void staleContentCompletionCannotClaimTheNewSelectionWasLoaded() {
        var state = new ScriptPackResourceState();
        Object repository = new Object();
        state.select(repository, "a");
        var first = state.capture();
        state.select(repository, "b");
        assertFalse(state.applied(first));
        assertTrue(state.select(repository, "b"));
        assertTrue(state.applied(state.capture()));
        assertFalse(state.select(repository, "b"));
        // A late old reload may actually replace resources; it must make the new desired content dirty again.
        assertFalse(state.applied(first));
        assertTrue(state.select(repository, "b"));
    }

    @Test void previousServerCompletionCannotClearNewServerWork() {
        var state = new ScriptPackResourceState();
        Object oldRepository = new Object(), newRepository = new Object();
        state.select(oldRepository, "a");
        var previous = state.capture();
        assertTrue(state.select(newRepository, "a"));
        assertFalse(state.applied(previous));
        assertTrue(state.select(newRepository, "a"));
        assertTrue(state.applied(state.capture()));
        assertFalse(state.select(newRepository, "a"));
    }

    @Test void resetRetiresPendingWorkAndStartsAnEmptyRepositoryClean() {
        var state = new ScriptPackResourceState();
        state.select(new Object(), "a");
        var previous = state.capture();
        state.reset();
        assertFalse(state.applied(previous));
        assertFalse(state.select(new Object(), ""));
    }

    @Test void removingLastPackAlsoRequiresASuccessfulReload() {
        var state = new ScriptPackResourceState();
        Object repository = new Object();
        state.select(repository, "a");
        state.applied(state.capture());
        assertTrue(state.select(repository, ""));
        assertTrue(state.select(repository, ""));
        assertTrue(state.applied(state.capture()));
        assertFalse(state.select(repository, ""));
    }
}
