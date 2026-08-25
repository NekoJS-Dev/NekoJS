package com.tkisor.nekojs.platform.compat;

import org.junit.jupiter.api.Test;

import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W5/A8 接线回归（26.x 专属）：26-shared 的 LevelExtension 经
 * {@link McVersionCompat} 取 LIGHTNING_BOLT 类型，26.1（EntityType）与
 * 26.2（EntityTypes）各提供一份 provider。1.21.1 的 LevelExtension 是独立实现，
 * 不经过该门面，也不提供 services 条目——所以本测试只放在 26-shared 测试树
 * （仅 26.1/26.2 编译执行）。
 */
class McVersionCompatWiringTest {

    @Test
    void versionModuleProvidesVersionCompatImpl() {
        assertTrue(ServiceLoader.load(McVersionCompat.Impl.class, McVersionCompat.class.getClassLoader())
                        .findFirst().isPresent(),
                "no McVersionCompat.Impl provider on this 26.x platform's test classpath: "
                        + "expected META-INF/services entry from neoforge-26.1/26.2");
    }
}
