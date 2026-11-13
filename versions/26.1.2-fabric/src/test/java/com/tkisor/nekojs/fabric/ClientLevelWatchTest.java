package com.tkisor.nekojs.fabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fixture for the rule that the first join's data is not wrongly cleared by the enter-world hook,
 * local to the fabric node and duplicated on both fabric nodes. {@link ClientLevelWatch} is the
 * fabric-side guard that clears only when leaving an old world: NeoForge hooks client level
 * unload (fired on disconnect and on dimension change, while a first join has no unload event and
 * so never clears), and fabric has no matching event, so it compares client world instances
 * instead. The state machine:
 * <ul>
 *   <li>null to world (first join): no clear, or the pdata mirror/clientData pushed during the
 *       join would be wiped;</li>
 *   <li>world to world (dimension change) and world to null (disconnect): clear;</li>
 *   <li>same instance on repeated ticks: no change.</li>
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
        // FabricPDataSync (the pdata mirror) and FabricPlayNetwork (the clientData store) each hold their
        // own guard, so they never interfere: one domain consumes its own change sequence without
        // swallowing the other's tick observation.
        ClientLevelWatch pdataWatch = new ClientLevelWatch();
        ClientLevelWatch clientDataWatch = new ClientLevelWatch();
        pdataWatch.leftPreviousLevel(OVERWORLD);

        assertFalse(clientDataWatch.leftPreviousLevel(OVERWORLD),
                "the other domain's watch still sees this as its first join");
    }
}
