package com.tkisor.nekojs.platform.compat;

import org.junit.jupiter.api.Test;

import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W5/A8 接线回归：共享代码经 ServiceLoader 取 per-version compat 实现，
 * services 文件缺失时首次调用只会抛 IllegalStateException——编译期完全不可见，
 * 到运行期才炸（错误面板 toast / 主机码 / 闪电生成全部失效）。这里在测试 classpath
 * 上直接验证 provider 存在；不触发 MC 类初始化（provider 实例化是惰性解析方法体）。
 *
 * <p>本测试在三个 NeoForge 平台（1.21.1/26.1/26.2）的测试任务里各跑一遍——
 * 每个平台的 jar 只应携带自己版本的 services 条目。
 */
class McClientCompatWiringTest {

    @Test
    void versionModuleProvidesClientCompatImpl() {
        assertTrue(ServiceLoader.load(McClientCompat.Impl.class, McClientCompat.class.getClassLoader())
                        .findFirst().isPresent(),
                "no McClientCompat.Impl provider on this platform's test classpath: "
                        + "expected META-INF/services entry from the version module "
                        + "(neoforge-1.21.1/26.1/26.2)");
    }
}
