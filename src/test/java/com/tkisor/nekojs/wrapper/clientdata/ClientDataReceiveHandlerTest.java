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
 * 票 18 AC4 接收侧：{@link ClientDataMessageHandler#handleOnClient} 的坏包丢弃语义——
 * 网络线程收包经 {@code enqueueWork} hop 到主线程后解析 JSON：
 * <ul>
 *   <li>合法 JSON 文本 → 覆盖写入 {@link ClientDataStore#SHARED}；</li>
 *   <li>非法 JSON 文本 → 丢弃并记 WARN（不炸主线程任务、store 无该 key）——
 *       「WARN 记录」面见 REPORT characterization，本测试钉「丢弃 + 不抛 + 不写入」；</li>
 *   <li>处理必经 enqueueWork hop（主线程可见性，与脚本通道同形——票 17 的 hop 先例）。</li>
 * </ul>
 * fabric 等价面（FabricPlayNetwork.acceptClientData 同语义）由 fabric 节点本地
 * ClientDataAcceptFixture 钉住。
 */
class ClientDataReceiveHandlerTest {

    /** ImmediateContext 桩（票 17 ScriptPayloadRegistrationShapeTest 同形）：enqueueWork 立即执行。 */
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

        // 同一任务的后续包不受坏包影响：主线程任务链不被打断
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
