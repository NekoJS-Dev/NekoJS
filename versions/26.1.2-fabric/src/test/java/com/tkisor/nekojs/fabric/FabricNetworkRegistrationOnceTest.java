// fabric 节点本地 registration-once 测试（票 17）：`//? if fabric` 守卫对 active 节点
// （26.1.2 NeoForge，直编共享树磁盘）是惰性注释，会在错误平台上编译执行，所以放节点本地
// test 树（标准 source set 布局，天然只属于本节点）。两 fabric 节点各一份同名同体测试
// （FabricVersionCompatWiringTest 的节点分发先例）。
package com.tkisor.nekojs.fabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 票 17 AC1 的 fabric 侧 JVM fixture：{@link FabricPlayNetwork#registerServer()} 的
 * payload 类型注册是「平台只允许一次」——第二次注册同一 payload type 被 fabric-api
 * 以 {@link IllegalArgumentException} 拒绝（duplicate registration）。这把「重复注册不可能
 * 静默发生」钉在平台 API 契约上；「registerServer 只从 mod entrypoint 调用一次」由共享树
 * {@code NetworkRegistrationSourceTraceTest} 的源码 trace 钉住，平台 event/enipoint 的
 * 单次触发属 loader 契约（REPORT characterization）。
 *
 * <p>本测试只触碰 fabric-api 的静态注册表（测试 JVM 私有），不启动游戏。
 */
class FabricNetworkRegistrationOnceTest {

    @Test
    void secondRegisterServerIsRejectedByThePlatformAsDuplicate() {
        assertDoesNotThrow(FabricPlayNetwork::registerServer,
                "first registration from the loader init path must succeed");

        assertThrows(IllegalArgumentException.class, FabricPlayNetwork::registerServer,
                "re-registering the same payload types must be rejected loudly by fabric-api, "
                        + "so reload paths can never silently duplicate registrations");
    }
}
