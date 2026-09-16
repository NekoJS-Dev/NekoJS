package com.tkisor.nekojs.fabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 票 18 AC5 的「首次进服数据不被进入世界钩子误删」fixture（fabric 节点本地，两 fabric
 * 节点同体分发）：{@link ClientLevelWatch} 是 fabric 侧「离开旧世界才清」守卫——NeoForge
 * 挂 client level unload（断线/切维度都触发；首次进服没有 unload 事件，天然不误删），
 * fabric 无对应事件，以客户端世界实例变化等价。状态机语义：
 * <ul>
 *   <li>null→世界（首次进服）：不清——否则刚随进服推下来的 pdata mirror/clientData 被抹掉；</li>
 *   <li>世界→世界（切维度）/ 世界→null（断线）：清；</li>
 *   <li>同实例重复 tick：不动。</li>
 * </ul>
 */
class ClientLevelWatchTest {

    private static final Object OVERWORLD = new Object();
    private static final Object NETHER = new Object();

    @Test
    void firstJoinDoesNotReportLeftPreviousLevel() {
        ClientLevelWatch watch = new ClientLevelWatch();
        assertFalse(watch.leftPreviousLevel(null), "no level yet, nothing to leave");
        assertFalse(watch.leftPreviousLevel(OVERWORLD),
                "null→level is the first join: must NOT clear freshly synced data");
        assertFalse(watch.leftPreviousLevel(OVERWORLD), "same instance ticks are no-ops");
    }

    @Test
    void dimensionSwitchAndDisconnectReportLeftPreviousLevel() {
        ClientLevelWatch watch = new ClientLevelWatch();
        watch.leftPreviousLevel(OVERWORLD); // first join

        assertTrue(watch.leftPreviousLevel(NETHER), "level→level is a dimension switch: clear");
        assertFalse(watch.leftPreviousLevel(NETHER));
        assertTrue(watch.leftPreviousLevel(null), "level→null is a disconnect: clear");
        assertFalse(watch.leftPreviousLevel(null));
        assertFalse(watch.leftPreviousLevel(OVERWORLD), "re-joining after disconnect is a fresh join: no clear");
    }

    @Test
    void instancesAreIndependent() {
        // FabricPDataSync（pdata mirror）与 FabricPlayNetwork（clientData store）各持一个守卫：
        // 互不串扰——一个域消费自己的变化序列，不吞掉另一个域的 tick 观测
        ClientLevelWatch pdataWatch = new ClientLevelWatch();
        ClientLevelWatch clientDataWatch = new ClientLevelWatch();
        pdataWatch.leftPreviousLevel(OVERWORLD);

        assertFalse(clientDataWatch.leftPreviousLevel(OVERWORLD),
                "the other domain's watch still sees this as its first join");
    }
}
