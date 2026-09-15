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
 * 票 17 AC1/AC3 的 NeoForge compat seam fixture（26.x 形态）：脚本自定义通道
 * {@link NekoScriptPayload} 的注册面经 {@link McPlatformCompat.Impl#registerScriptPayload}
 * 恰好注册一个 bidirectional payload，注册时携带的两个 handler 都按「网络线程 →
 * enqueueWork → 中立投递」的既有形状工作。
 *
 * <p>「注册只发生在 loader 原生初始化时机（RegisterPayloadHandlersEvent）」的时机面由
 * {@code NetworkRegistrationSourceTraceTest} 的源码 trace 钉住；平台对该 event 的
 * 一次性触发属 loader 契约，不在 JVM 单测范围（见 REPORT characterization）。
 */
class ScriptPayloadRegistrationShapeTest {

    /** 记录 playBidirectional 调用的 registrar 桩：不触平台注册表，只留证。 */
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

    /** IPayloadContext 桩：enqueueWork 立即执行（模拟主线程切换），player 无。 */
    static final class ImmediateContext implements IPayloadContext {
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
        IPayloadContext context = new ImmediateContext();
        assertDoesNotThrow(() ->
                ((IPayloadHandler<NekoScriptPayload>) registration.serverHandler()).handle(payload, context));
        assertEquals(1, serverHits.get(),
                "serverbound packet must reach the SERVER bus after the enqueueWork hop");

        // clientbound handler 同形状：enqueueWork 后投 CLIENT 总线（本 JVM 无 CLIENT 监听器，
        // 断言不抛即「主线程任务不炸」；CLIENT 总线路由由 NetworkGenerationRoutingTest 覆盖）
        assertDoesNotThrow(() ->
                ((IPayloadHandler<NekoScriptPayload>) registration.clientHandler()).handle(payload, context));
        assertEquals(1, serverHits.get(), "clientbound handler must not touch the SERVER bus");
    }
}
//?}
//?}
