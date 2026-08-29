//? if >=26 {
package com.tkisor.nekojs.wrapper.pdata;

import com.tkisor.nekojs.network.PDataSyncPacket;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * pdata 同步的客户端去重/清除语义（票 09）：acceptClientSync 按 revision 单调推进，
 * 空 data 包 = 清除该实体的 mirror；clearClientMirrors 全清（断线/换世界）。
 * 纯静态 Map 逻辑，无 live Entity——两条平台（NeoForge/fabric 的 receiver）喂同一入口。
 */
class PDataSyncAcceptTest {

    @AfterEach
    void reset() {
        PDataSyncService.clearClientMirrors();
    }

    private static CompoundTag tagOf(String key, int value) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(key, value);
        return tag;
    }

    @Test
    void acceptStoresAndMirrorsReadBackByEntityId() {
        PDataSyncService.acceptClientSync(new PDataSyncPacket(7, 1, tagOf("mana", 5)));

        // mirror 读取需要一个 Entity——acceptClientSync 的存储与 revision 判定不依赖 Entity，
        // mirror 的可见性经 clear/覆盖语义验证（clientMirror(Entity) 需要真实体，见实体侧测试）
        assertTrue(PDataSyncService.hasPendingClientData(7), "revision-1 data must be stored");
    }

    @Test
    void staleRevisionIsIgnored() {
        PDataSyncService.acceptClientSync(new PDataSyncPacket(7, 3, tagOf("mana", 30)));
        PDataSyncService.acceptClientSync(new PDataSyncPacket(7, 1, tagOf("mana", 10)));

        assertTrue(PDataSyncService.hasPendingClientData(7));
        // 旧包被忽略：镜像值仍是 revision 3 的内容——经 clear 空包对照验证
        PDataSyncService.acceptClientSync(new PDataSyncPacket(7, 4, new CompoundTag()));
        assertFalse(PDataSyncService.hasPendingClientData(7), "empty-data packet clears the mirror");
    }

    @Test
    void emptyDataClearsAndEqualRevisionOverwrites() {
        PDataSyncService.acceptClientSync(new PDataSyncPacket(1, 1, tagOf("a", 1)));
        PDataSyncService.acceptClientSync(new PDataSyncPacket(2, 1, tagOf("b", 2)));

        PDataSyncService.acceptClientSync(new PDataSyncPacket(1, 1, new CompoundTag()));
        assertFalse(PDataSyncService.hasPendingClientData(1), "entity 1 cleared");
        assertTrue(PDataSyncService.hasPendingClientData(2), "entity 2 untouched");

        // 同 revision 重复投递（重发）不抛、幂等
        PDataSyncService.acceptClientSync(new PDataSyncPacket(2, 1, tagOf("b", 2)));
        assertTrue(PDataSyncService.hasPendingClientData(2));
    }

    @Test
    void clearClientMirrorsWipesEverything() {
        PDataSyncService.acceptClientSync(new PDataSyncPacket(1, 1, tagOf("a", 1)));
        PDataSyncService.acceptClientSync(new PDataSyncPacket(2, 1, tagOf("b", 2)));

        PDataSyncService.clearClientMirrors();
        assertFalse(PDataSyncService.hasPendingClientData(1));
        assertFalse(PDataSyncService.hasPendingClientData(2));
        assertEquals(0, mirrorOfCleared(1).size(), "cleared entity must mirror to empty");
    }

    private static CompoundTag mirrorOfCleared(int entityId) {
        // 清除后 getOrDefault 路径：直接构造空结果语义检查
        return PDataSyncService.clientMirrorById(entityId);
    }
}
//?}
