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
 * Fabric side of client data acceptance, local to the fabric node and duplicated on both fabric
 * nodes. {@code FabricPlayNetwork.acceptClientData} is the main-thread entry point the client
 * receiver calls after the {@code context.client().execute()} hop, with the same semantics as the
 * NeoForge ClientDataMessageHandler: valid JSON text overwrites the SHARED store; invalid JSON
 * text is dropped without throwing and without writing, is logged at WARN, and does not affect
 * later packets. The receiver's hop to the main thread before parsing is pinned by the source
 * shape, since fabric receivers always call {@code context.client().execute(...)}.
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

        // A bad packet does not break the main-thread task chain: later packets still write.
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
