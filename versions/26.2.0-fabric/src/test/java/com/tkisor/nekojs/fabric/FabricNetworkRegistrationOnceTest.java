// Fabric node-local registration-once test. The `//? if fabric` guard is a lazy comment for the
// active node (26.1.2 NeoForge, which compiles the shared tree from disk), so it would compile and
// run on the wrong platform; the test therefore lives in the node-local test tree, which the
// standard source set layout mounts for this node only. Both fabric nodes carry one copy of the
// same-body test, following the FabricVersionCompatWiringTest precedent.
package com.tkisor.nekojs.fabric;

import com.tkisor.nekojs.network.PlayPacketDispatchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Fabric JVM fixture for the first registration-once point. Registering a payload type through
 * {@link FabricPlayNetwork#registerServer()} is allowed once per platform: a second registration of
 * the same payload type is rejected by fabric-api with {@link IllegalArgumentException} (duplicate
 * registration). That pins "duplicate registration cannot happen silently" to the platform API
 * contract. "registerServer is called exactly once, from the mod entrypoint" is pinned by the
 * source trace in {@code NetworkRegistrationSourceTraceTest} in the shared tree; a single fire of
 * the platform event/endpoint is a loader contract (report characterization).
 *
 * <p>This test only touches the fabric-api static registry, which is private to the test JVM, and
 * never starts the game.
 */
class FabricNetworkRegistrationOnceTest {

    /**
     * registerServer installs the real FabricDispatcher into the global PlayPacketDispatchers, so the
     * test restores NOOP afterwards and leaves no static wiring behind.
     */
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
