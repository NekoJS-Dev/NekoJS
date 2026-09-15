// fabric 节点本地 wiring 测试（票 31）：镜像 NeoForge 侧 McVersionCompatWiringTest 的
// 用途——ServiceLoader 缺 provider 时首次调用只抛 IllegalStateException，编译期不可见。
// 不放共享测试树的原因：`//? if fabric` 守卫对 active 节点（26.1.2 NeoForge，直编共享树
// 磁盘）是惰性注释，会在错误平台上编译执行；节点本地 test 树由标准 source set 布局挂载，
// 天然只属于本节点。两 fabric 节点各一份同名同体测试（同 golden fixture 的节点分发先例）。
package com.tkisor.nekojs.platform.compat;

import org.junit.jupiter.api.Test;

import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 26.1.2-fabric 的 {@link McVersionCompat} 接线回归：本节点 jar/test classpath 必须携带
 * {@code Fabric261VersionCompat} services 条目——共享树 {@code inject.MixinLevel} 注入的
 * {@code LevelExtension.neko$spawnLightning} 经该门面取闪电实体类型，缺 provider 即
 * ServiceLoader 潜伏初始化崩溃（d.ts 已宣传 spawnLightning）。不触发 MC 类初始化
 *（provider 实例化是惰性解析方法体）。
 */
class FabricVersionCompatWiringTest {

    @Test
    void fabricNodeProvidesVersionCompatImpl() {
        assertTrue(ServiceLoader.load(McVersionCompat.Impl.class, McVersionCompat.class.getClassLoader())
                        .findFirst().isPresent(),
                "no McVersionCompat.Impl provider on 26.1.2-fabric test classpath: "
                        + "expected META-INF/services entry for Fabric261VersionCompat");
    }
}
