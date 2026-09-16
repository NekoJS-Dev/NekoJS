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
 * 票 18 AC4 的 ClientData 发送面域语义 fixture：{@code ClientData.sync} 经
 * {@link PlayPacketDispatchers} 装配推送——合法 JSON 类型走 key+JSON 文本 wire
 * （wire hex 本体由票 17 PayloadWireFormatGoldenTest 钉住，本测试钉域语义）；
 * 非旧契约 JSON 类型显式失败；超限值（序列化超 32768 字符）显式失败且不发送；
 * 同 key 覆盖、key 校验与坏 key 拒绝。
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
        // 序列化文本恰在上限内：32766 字符字符串 + 两个引号 = 32768
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
        // 接收侧等价面：平台 handler 解析 JSON 文本后写入 SHARED（v1 语义：同 key 后到者胜）
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
