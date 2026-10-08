package com.tkisor.nekojs.core.dynamic.facade;

import com.tkisor.nekojs.core.dynamic.plan.DynamicAdapterRequest;
import com.tkisor.nekojs.core.dynamic.txn.DynamicRegistryAdapter;
import com.tkisor.nekojs.core.dynamic.txn.DynamicSyncMessage;
import com.tkisor.nekojs.core.dynamic.txn.DynamicSyncTransport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DynamicActivationOwnerReleaseTest {
    private static final DynamicRegistryAdapter ADAPTER = new DynamicRegistryAdapter() {
        public void prepareActivation(List<DynamicAdapterRequest> requests) {}
        public void activate(List<DynamicAdapterRequest> requests) {}
        public void rollbackActivation(List<DynamicAdapterRequest> requests) {}
    };

    private static final class Transport implements DynamicSyncTransport, AutoCloseable {
        int closes;
        public List<String> participants() { return List.of(); }
        public void send(String id, DynamicSyncMessage message) {}
        public void broadcast(DynamicSyncMessage message) {}
        public void close() { closes++; }
    }

    @Test
    void serverStopReleasesConnectionOwnerOnce() {
        var runtime = new DynamicRegistryFacadeRuntime();
        var transport = new Transport();
        runtime.bindActivationEngine(ADAPTER, transport, 100, () -> 0);
        runtime.clearActivationEngine("server-stopped");
        runtime.clearActivationEngine("server-stopped");
        assertNull(runtime.activationEngine());
        assertEquals(1, transport.closes);
    }

    @Test
    void rebindingReleasesPreviousConnectionOwner() {
        var runtime = new DynamicRegistryFacadeRuntime();
        var first = new Transport();
        var second = new Transport();
        runtime.bindActivationEngine(ADAPTER, first, 100, () -> 0);
        runtime.bindActivationEngine(ADAPTER, second, 100, () -> 0);
        assertEquals(1, first.closes);
        assertEquals(0, second.closes);
        runtime.clearActivationEngine("server-stopped");
        assertEquals(1, second.closes);
    }

    @Test
    void facadeIsCloseableForRuntimeRootDomainRelease() {
        assertTrue(AutoCloseable.class.isAssignableFrom(DynamicRegistryFacadeRuntime.class));
    }
}
