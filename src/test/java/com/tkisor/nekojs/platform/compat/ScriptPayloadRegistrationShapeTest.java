//? if neoforge {
//? if >=26 {
package com.tkisor.nekojs.platform.compat;

import com.tkisor.nekojs.bindings.event.NetworkEvents;
import com.tkisor.nekojs.network.NekoScriptPayload;
import com.tkisor.nekojs.network.NetworkMessageHandler;
import com.tkisor.nekojs.wrapper.network.NetworkDataEventJS;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * NeoForge compat fixture (26.x shape) for the script custom channel: registering
 * {@link NekoScriptPayload} through {@link McPlatformCompat.Impl#registerScriptPayload} registers
 * exactly one bidirectional payload, and both handlers it carries work in the existing shape of
 * network thread -> {@code enqueueWork} -> neutral delivery.
 *
 * <p>{@code NetworkRegistrationSourceTraceTest} pins by source trace that registration happens only
 * at the loader's native initialisation point ({@code RegisterPayloadHandlersEvent}). The loader
 * firing that event exactly once is a loader contract and is out of scope for a JVM unit test.
 */
class ScriptPayloadRegistrationShapeTest {

    /** Registrar stub that records {@code playBidirectional} calls. It never touches the platform
     * registry; it only keeps evidence. */
    static final class RecordingRegistrar extends PayloadRegistrar {
        record Bidirectional(String id, IPayloadHandler<?> serverHandler, IPayloadHandler<?> clientHandler) {}

        final List<Bidirectional> bidirectional = new ArrayList<>();

        RecordingRegistrar() {
            super("1");
        }

        @Override
        public <T extends CustomPacketPayload> PayloadRegistrar playBidirectional(
                CustomPacketPayload.Type<T> type,
                StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                IPayloadHandler<T> serverHandler,
                IPayloadHandler<T> clientHandler) {
            bidirectional.add(new Bidirectional(type.id().toString(), serverHandler, clientHandler));
            return this;
        }
    }

    /**
     * {@link IPayloadContext} stub: {@code enqueueWork} runs immediately (simulating the switch to
     * the main thread), and there is no player. {@code enqueueWorkCalls} counts the hops, so a
     * handler that regresses to posting on the bus directly and bypasses {@code enqueueWork} leaves
     * the count unchanged; the tests then use that count to assert that handling a hit always takes
     * the hop.
     */
    static final class ImmediateContext implements IPayloadContext {
        int enqueueWorkCalls;

        @Override
        public net.neoforged.neoforge.common.extensions.ICommonPacketListener listener() {
            throw new UnsupportedOperationException("not used by the script payload handler");
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
            return PacketFlow.SERVERBOUND;
        }

        @Override
        public void handle(CustomPacketPayload payload) {
            throw new UnsupportedOperationException("not used by the script payload handler");
        }

        @Override
        public void finishCurrentTask(net.minecraft.server.network.ConfigurationTask.Type type) {
            throw new UnsupportedOperationException("not used by the script payload handler");
        }
    }

    private com.tkisor.nekojs.api.event.DispatchEventBus<NetworkDataEventJS, String> serverBus;
    private final List<com.tkisor.nekojs.api.event.EventListenerToken<NetworkDataEventJS>> tokens =
            new CopyOnWriteArrayList<>();

    @AfterEach
    void cleanup() {
        for (var token : tokens) {
            serverBus.unregister(token);
        }
        tokens.clear();
    }

    @SuppressWarnings("unchecked")
    private void listenOnServerBus(String channel, Runnable onEvent) {
        serverBus = (com.tkisor.nekojs.api.event.DispatchEventBus<NetworkDataEventJS, String>) NetworkEvents.SERVER.bus();
        tokens.add(serverBus.listen(channel, event -> onEvent.run()));
    }

    @Test
    void compatRegistersExactlyOneBidirectionalScriptPayload() {
        RecordingRegistrar registrar = new RecordingRegistrar();
        McPlatformCompat.get().registerScriptPayload(registrar);

        assertEquals(1, registrar.bidirectional.size(),
                "registerScriptPayload must register exactly one payload (once per loader init)");
        assertEquals("nekojs:script_payload", registrar.bidirectional.get(0).id(),
                "the script channel payload must keep its legacy wire id");
    }

    @Test
    @SuppressWarnings("unchecked")
    void registeredHandlersHopMainThreadThenRouteIntoOwnerBus() {
        RecordingRegistrar registrar = new RecordingRegistrar();
        McPlatformCompat.get().registerScriptPayload(registrar);
        RecordingRegistrar.Bidirectional registration = registrar.bidirectional.get(0);

        AtomicInteger serverHits = new AtomicInteger();
        listenOnServerBus("probe", serverHits::incrementAndGet);

        NekoScriptPayload payload = new NekoScriptPayload("probe", new CompoundTag());
        ImmediateContext context = new ImmediateContext();
        assertDoesNotThrow(() ->
                ((IPayloadHandler<NekoScriptPayload>) registration.serverHandler()).handle(payload, context));
        assertEquals(1, context.enqueueWorkCalls,
                "serverbound delivery must go through enqueueWork, not post on the network thread");
        assertEquals(1, serverHits.get(),
                "serverbound packet must reach the SERVER bus after the enqueueWork hop");

        // The clientbound handler has the same shape: it posts to the CLIENT bus after enqueueWork.
        // This JVM has no CLIENT listeners, so the assertion is only that nothing throws, meaning
        // the main-thread task does not blow up; CLIENT bus routing is covered by
        // NetworkGenerationRoutingTest.
        assertDoesNotThrow(() ->
                ((IPayloadHandler<NekoScriptPayload>) registration.clientHandler()).handle(payload, context));
        assertEquals(2, context.enqueueWorkCalls,
                "clientbound delivery must also go through enqueueWork");
        assertEquals(1, serverHits.get(), "clientbound handler must not touch the SERVER bus");
    }
}
//?}
//?}
