//? if neoforge {
package com.tkisor.nekojs.wrapper.clientdata;

import com.tkisor.nekojs.network.ClientDataMessageHandler;
import com.tkisor.nekojs.network.ClientDataSyncPacket;
import com.tkisor.nekojs.wrapper.clientdata.ClientDataStore;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Receive side of client data: what {@link ClientDataMessageHandler#handleOnClient} does with a bad
 * packet. The network thread receives the packet, hops to the main thread through
 * {@code enqueueWork}, and then parses the JSON:
 * <ul>
 *   <li>valid JSON text writes over {@link ClientDataStore#SHARED};</li>
 *   <li>invalid JSON text is dropped and logged as WARN, without breaking the main-thread task and
 *       without leaving the key in the store; the WARN record itself is covered by
 *       characterization, while this test pins drop, no throw and no write;</li>
 *   <li>handling always goes through the {@code enqueueWork} hop for main-thread visibility, the
 *       same shape as the script channel.</li>
 * </ul>
 * The fabric equivalent (FabricPlayNetwork.acceptClientData with the same semantics) is pinned by
 * the fabric node's local ClientDataAcceptFixture.
 */
class ClientDataReceiveHandlerTest {

    /** ImmediateContext stub, the same shape as ScriptPayloadRegistrationShapeTest: {@code enqueueWork}
     * runs immediately. */
    static final class ImmediateContext implements IPayloadContext {
        int enqueueWorkCalls;

        @Override
        public net.neoforged.neoforge.common.extensions.ICommonPacketListener listener() {
            throw new UnsupportedOperationException("not used by the client data handler");
        }

        @Override
        public net.minecraft.world.entity.player.Player player() {
            return null;
        }

        @Override
        public CompletableFuture<Void> enqueueWork(Runnable work) {
            enqueueWorkCalls++;
            work.run();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public <T> CompletableFuture<T> enqueueWork(java.util.function.Supplier<T> work) {
            return CompletableFuture.completedFuture(work.get());
        }

        @Override
        public PacketFlow flow() {
            return PacketFlow.CLIENTBOUND;
        }

        @Override
        public void handle(CustomPacketPayload payload) {
            throw new UnsupportedOperationException("not used by the client data handler");
        }

        @Override
        public void finishCurrentTask(net.minecraft.server.network.ConfigurationTask.Type type) {
            throw new UnsupportedOperationException("not used by the client data handler");
        }
    }

    @AfterEach
    void cleanStore() {
        ClientDataStore.SHARED.clear();
    }

    @Test
    void validPacketHopsMainThreadThenWritesTheStore() {
        ImmediateContext context = new ImmediateContext();
        ClientDataMessageHandler.handleOnClient(
                new ClientDataSyncPacket("hud:mana", "{\"cur\":12}"), context);

        assertEquals(1, context.enqueueWorkCalls,
                "client data handling must go through the enqueueWork main-thread hop");
        assertEquals(Map.of("cur", 12L), ClientDataStore.SHARED.get("hud:mana"));
    }

    @Test
    void malformedJsonPacketIsDroppedWithoutThrowingOrWriting() {
        ImmediateContext context = new ImmediateContext();
        assertDoesNotThrow(() -> ClientDataMessageHandler.handleOnClient(
                new ClientDataSyncPacket("hud:broken", "{\"cur\":"), context));
        assertFalse(ClientDataStore.SHARED.has("hud:broken"),
                "malformed JSON must be discarded, not stored");

        // Later packets in the same task are unaffected by the bad packet: the main-thread task chain
        // is not interrupted
        ClientDataMessageHandler.handleOnClient(
                new ClientDataSyncPacket("hud:ok", "7"), context);
        assertEquals(7L, ClientDataStore.SHARED.get("hud:ok"));
    }

    @Test
    void jsonNullValueIsStoredAsNullSlot() {
        ImmediateContext context = new ImmediateContext();
        ClientDataMessageHandler.handleOnClient(
                new ClientDataSyncPacket("hud:nil", "null"), context);
        assertTrue(ClientDataStore.SHARED.has("hud:nil"), "JSON null occupies the key");
        assertNull(ClientDataStore.SHARED.get("hud:nil"), "and reads back as null");
    }
}
//?}
