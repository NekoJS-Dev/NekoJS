package com.tkisor.nekojs.fabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fixture for "the data pushed on the first join is not deleted by mistake by the enter-world hook",
 * node-local on fabric and shipped in the same body to both fabric nodes. {@link ClientLevelWatch}
 * is the fabric-side guard that clears only after leaving the old level. On NeoForge the clear
 * hangs off client level unload, which fires on disconnect and on dimension change but not on the
 * first join, so the first join is safe there by construction; fabric has no matching event, so it
 * uses a change of the client level instance instead. The state machine:
 * <ul>
 *   <li>null to a level (first join): no clear, otherwise the pdata mirror/clientData just pushed
 *       down for the join would be wiped;</li>
 *   <li>level to level (dimension change) or level to null (disconnect): clear;</li>
 *   <li>repeated tick on the same instance: nothing.</li>
 * </ul>
 */
class ClientLevelWatchTest {

    private static final Object OVERWORLD = new Object();
    private static final Object NETHER = new Object();

    @Test
    void firstJoinDoesNotReportLeftPreviousLevel() {
        ClientLevelWatch watch = new ClientLevelWatch();
        assertFalse(watch.leftPreviousLevel(null), "no level yet, nothing to leave");
        assertFalse(watch.leftPreviousLevel(OVERWORLD),
                "null→level is the first join: must NOT clear freshly synced data");
        assertFalse(watch.leftPreviousLevel(OVERWORLD), "same instance ticks are no-ops");
    }

    @Test
    void dimensionSwitchAndDisconnectReportLeftPreviousLevel() {
        ClientLevelWatch watch = new ClientLevelWatch();
        watch.leftPreviousLevel(OVERWORLD); // first join

        assertTrue(watch.leftPreviousLevel(NETHER), "level→level is a dimension switch: clear");
        assertFalse(watch.leftPreviousLevel(NETHER));
        assertTrue(watch.leftPreviousLevel(null), "level→null is a disconnect: clear");
        assertFalse(watch.leftPreviousLevel(null));
        assertFalse(watch.leftPreviousLevel(OVERWORLD), "re-joining after disconnect is a fresh join: no clear");
    }

    @Test
    void instancesAreIndependent() {
        // FabricPDataSync (pdata mirror) and FabricPlayNetwork (clientData store) each hold their own
        // guard, so they do not interfere: each domain consumes its own change sequence and does not
        // swallow the other's tick observation.
        ClientLevelWatch pdataWatch = new ClientLevelWatch();
        ClientLevelWatch clientDataWatch = new ClientLevelWatch();
        pdataWatch.leftPreviousLevel(OVERWORLD);

        assertFalse(clientDataWatch.leftPreviousLevel(OVERWORLD),
                "the other domain's watch still sees this as its first join");
    }
}
