// Node-local registration-once test for the fabric node. A `//? if fabric` guard is an inert
// comment for the active node (26.1.2 NeoForge, which compiles the shared tree from disk), so the
// code would compile and run on the wrong platform; hence it lives in the node-local test tree
// (standard source set layout, so it belongs only to this node). Both fabric nodes carry a copy
// with the same name and body.
package com.tkisor.nekojs.fabric;

import com.tkisor.nekojs.network.PlayPacketDispatchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Fabric-side JVM fixture for the rule that a payload type can be registered only once.
 * {@link FabricPlayNetwork#registerServer()} registers its payload types, and a second
 * registration of the same payload type is rejected by fabric-api with an
 * {@link IllegalArgumentException} (duplicate registration), so duplicate registration cannot
 * happen silently. That {@code registerServer} is called once from the mod entrypoint is pinned by
 * the shared tree's {@code NetworkRegistrationSourceTraceTest}; the loader contract makes the
 * platform event and entrypoint fire once.
 *
 * <p>This test only touches fabric-api's static registry, private to the test JVM, and does not
 * start the game.
 */
class FabricNetworkRegistrationOnceTest {

    /** registerServer installs a real FabricDispatcher into the global PlayPacketDispatchers, so this
     * restores NOOP afterwards and leaves no static wiring behind. */
    @AfterEach
    void restoreNoopDispatcher() {
        PlayPacketDispatchers.install(PlayPacketDispatchers.NOOP);
    }

    @Test
    void secondRegisterServerIsRejectedByThePlatformAsDuplicate() {
        assertDoesNotThrow(FabricPlayNetwork::registerServer,
                "first registration from the loader init path must succeed");

        assertThrows(IllegalArgumentException.class, FabricPlayNetwork::registerServer,
                "re-registering the same payload types must be rejected loudly by fabric-api, "
                        + "so reload paths can never silently duplicate registrations");
    }
}
