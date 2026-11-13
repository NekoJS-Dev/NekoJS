package com.tkisor.nekojs.wrapper.clientdata;

import com.tkisor.nekojs.network.ClientDataSyncPacket;
import com.tkisor.nekojs.network.PlayPacketDispatcher;
import com.tkisor.nekojs.network.PlayPacketDispatchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fixture for the domain semantics of the ClientData send side: {@code ClientData.sync} assembles a
 * push through {@link PlayPacketDispatchers}. A valid JSON type goes over the wire as key plus JSON
 * text; the wire hex itself is pinned by PayloadWireFormatGoldenTest and this test pins the domain
 * semantics. A type that is not legacy-contract JSON fails explicitly. A value over the limit
 * (serialized text longer than 32768 characters) fails explicitly and is not sent. The same key
 * overwrites, and key validation rejects a bad key.
 */
class ClientDataSyncDomainTest {

    private static final class RecordingDispatcher implements PlayPacketDispatcher {
        final List<ClientDataSyncPacket> broadcast = new ArrayList<>();

        @Override
        public void sendToPlayer(net.minecraft.server.level.ServerPlayer player,
                                 net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
            throw new UnsupportedOperationException("not used in this fixture");
        }

        @Override
        public void sendToAllPlayers(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
            broadcast.add((ClientDataSyncPacket) payload);
        }

        @Override
        public void sendToPlayersTrackingEntityAndSelf(net.minecraft.world.entity.Entity entity,
                                                       net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
            throw new UnsupportedOperationException("not used in this fixture");
        }
    }

    private RecordingDispatcher dispatcher;

    @BeforeEach
    void installRecorder() {
        dispatcher = new RecordingDispatcher();
        PlayPacketDispatchers.install(dispatcher);
    }

    @AfterEach
    void restoreNoop() {
        PlayPacketDispatchers.install(PlayPacketDispatchers.NOOP);
        ClientDataStore.SHARED.clear();
    }

    @Test
    void syncPushesKeyValueThroughDispatcherWithLegacyShape() {
        java.util.LinkedHashMap<String, Object> value = new java.util.LinkedHashMap<>();
        value.put("current", 12);
        value.put("max", 20);
        ClientDataSyncJS.sync("hud:mana", value);

        assertEquals(1, dispatcher.broadcast.size());
        ClientDataSyncPacket packet = dispatcher.broadcast.get(0);
        assertEquals("hud:mana", packet.key());
        assertEquals("{\"current\":12,\"max\":20}", packet.json(),
                "values must serialize to compact JSON text on the wire");
    }

    @Test
    void nonJsonValuesFailExplicitlyWithoutSending() {
        assertThrows(IllegalArgumentException.class, () -> ClientDataSyncJS.sync("bad", new Object()),
                "host objects are not JSON types and must fail explicitly");
        assertThrows(IllegalArgumentException.class, () -> ClientDataSyncJS.sync("bad", System.out));
        assertEquals(0, dispatcher.broadcast.size(), "rejected values must not be sent");
    }

    @Test
    void oversizedValuesFailExplicitlyWithoutSending() {
        // Serialized text lands exactly on the limit: a 32766-character string plus two quotes is 32768
        ClientDataSyncJS.sync("big-ok", "x".repeat(32_766));
        assertEquals(1, dispatcher.broadcast.size(), "values at exactly the 32768-char budget must pass");

        assertThrows(IllegalArgumentException.class,
                () -> ClientDataSyncJS.sync("big-bad", "x".repeat(32_767)),
                "values serializing over 32768 chars must fail explicitly (not silently dropped)");
        assertEquals(1, dispatcher.broadcast.size(), "rejected oversized values must not be sent");
    }

    @Test
    void blankAndOverlongKeysAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> ClientDataSyncJS.sync(" ", 1));
        assertThrows(IllegalArgumentException.class, () -> ClientDataSyncJS.sync(null, 1));
        assertThrows(IllegalArgumentException.class, () -> ClientDataSyncJS.sync("k".repeat(257), 1));
        assertThrows(IllegalArgumentException.class, () -> new ClientDataSyncPacket("", "{}"),
                "blank keys are rejected at payload construction (decode-side defense)");
        assertEquals(0, dispatcher.broadcast.size());
    }

    @Test
    void sameKeyOverwritesOnTheClientStore() {
        // Receive-side equivalent: the platform handler parses the JSON text and writes it to SHARED;
        // for the same key the later write wins (v1 semantics)
        com.google.gson.JsonParser parser = new com.google.gson.JsonParser();
        ClientDataStore.SHARED.accept("hud:mana", parser.parse("{\"v\":1}"));
        ClientDataStore.SHARED.accept("hud:mana", parser.parse("{\"v\":2}"));
        assertEquals(Map.of("v", 2L), ClientDataStore.SHARED.get("hud:mana"),
                "re-pushed keys must overwrite on the client");
        assertEquals(1, ClientDataStore.SHARED.size());
    }

    @Test
    void storeClearIsComplete() {
        ClientDataStore.SHARED.accept("a", new com.google.gson.JsonPrimitive(1));
        ClientDataStore.SHARED.accept("b", new com.google.gson.JsonPrimitive(2));
        ClientDataStore.SHARED.clear();
        assertEquals(0, ClientDataStore.SHARED.size());
        assertFalse(ClientDataStore.SHARED.has("a"));
        assertTrue(ClientDataStore.SHARED.keys().isEmpty());
    }
}
