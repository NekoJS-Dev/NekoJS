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
 * 票 18 AC4 接收侧的 fabric 面（节点本地，两 fabric 节点同体分发）：
 * {@code FabricPlayNetwork.acceptClientData}（client receiver 在
 * {@code context.client().execute()} hop 后调用的主线程解析入口，与 NeoForge
 * ClientDataMessageHandler 同语义）：合法 JSON 文本覆盖写入 SHARED store；
 * 非法 JSON 文本丢弃、不抛（WARN 记录面见 REPORT characterization）、不写入，
 * 且不影响后续包。receiver 的「先 hop 主线程再解析」由源码形状钉住（fabric 侧
 * receiver 一律 {@code context.client().execute(...)}，票 17 trace 已覆盖同文件）。
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

        // 坏包不打断主线程任务链：后续包照常写入
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
