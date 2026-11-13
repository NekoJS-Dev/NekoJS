package com.tkisor.nekojs.fabric;

import com.tkisor.nekojs.network.ClientDataSyncPacket;
import com.tkisor.nekojs.wrapper.clientdata.ClientDataStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Fabric side of the receive path for the client-data feature (node-local; both Fabric nodes dispatch the same body):
 * {@code FabricPlayNetwork.acceptClientData}, the main-thread parse entry called by the client receiver after the
 * {@code context.client().execute()} hop, with the same semantics as the NeoForge ClientDataMessageHandler.
 * Valid JSON text is written over the SHARED store. Invalid JSON is dropped without throwing (the WARN record is
 * described in the feature report), nothing is written, and later packets are unaffected. The "hop to the main thread,
 * then parse" order is fixed by the source shape: Fabric receivers always use {@code context.client().execute(...)}.
 */
class FabricClientDataAcceptTest {

    @AfterEach
    void cleanStore() {
        ClientDataStore.SHARED.clear();
    }

    private static void accept(String key, String json) throws Exception {
        Method method = FabricPlayNetwork.class.getDeclaredMethod("acceptClientData", ClientDataSyncPacket.class);
        method.setAccessible(true);
        method.invoke(null, new ClientDataSyncPacket(key, json));
    }

    @Test
    void validJsonWritesTheSharedStore() throws Exception {
        accept("hud:mana", "{\"cur\":12}");
        assertEquals(Map.of("cur", 12L), ClientDataStore.SHARED.get("hud:mana"));
    }

    @Test
    void malformedJsonIsDroppedWithoutThrowingOrWriting() throws Exception {
        assertDoesNotThrow(() -> accept("hud:broken", "{\"cur\": "));
        assertFalse(ClientDataStore.SHARED.has("hud:broken"), "malformed JSON must be discarded");

        // A bad packet does not break the main-thread task chain: later packets still get written.
        accept("hud:ok", "7");
        assertEquals(7L, ClientDataStore.SHARED.get("hud:ok"));
    }

    @Test
    void sameKeyOverwritesAndNullValueKeepsTheSlot() throws Exception {
        accept("hud:mana", "1");
        accept("hud:mana", "2");
        assertEquals(2L, ClientDataStore.SHARED.get("hud:mana"), "same key: last write wins (v1 semantics)");
        accept("hud:nil", "null");
        assertNull(ClientDataStore.SHARED.get("hud:nil"), "JSON null reads back as null");
        assertEquals(2, ClientDataStore.SHARED.size(), "JSON null still occupies its key");
    }
}
