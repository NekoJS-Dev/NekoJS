package com.tkisor.nekojs.core.state;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Deletion test: the implicit cross-type fallback (the process-wide NekoGlobal static Map) is
 * deleted and stays deleted. There is no compatibility layer that writes both the old and the
 * new place or falls back implicitly; 1.2.0 made a clean cutover. This test pins the deletion
 * itself: if the class comes back, or the deletion is reverted, it fails at once and forces an
 * explicit decision.
 *
 * <p>The replacement semantics (the old cross-type global is no longer visible) are covered by
 * {@code Ticket10GlobalStateTest.sameTypeUsageUnchangedAndLegacyCrossTypeFallbackIsGone}.
 * This test only guards that there is a single implementation.
 */
class NekoGlobalRemovalTest {

    @Test
    void processWideNekoGlobalClassIsRemoved() {
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.tkisor.nekojs.bindings.static_access.NekoGlobal"),
                "the process-wide NekoGlobal static Map must stay deleted (ticket 10 deletion gate;"
                        + " single standard implementation lives in core.state)");
    }
}
