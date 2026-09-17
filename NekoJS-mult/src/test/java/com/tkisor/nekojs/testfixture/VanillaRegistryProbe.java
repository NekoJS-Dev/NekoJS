package com.tkisor.nekojs.testfixture;

import net.minecraft.server.Bootstrap;

/**
 * 裸 JUnit 能否初始化 vanilla 注册表的探测（26-shared 测试树共享；1.21.1 同一逻辑可用）。
 *
 * <p>无 FML Loader 的 JVM 里 26.x 的 {@code BuiltInRegistries}/{@code Items}/{@code DataComponents}
 * 类初始化链会抛错（26.x 还要求先 {@code Bootstrap.bootStrap()}，而 bootstrap 自身
 * 依赖 FML Loader）；1.21.1 的 bootstrap 无 FML 依赖，裸 JVM 通常可用——两侧都按本探针
 * 的实际结果决定真跑或跳过。涉及这些类的测试用 {@code Assumptions.assumeTrue(available())}
 * 守卫：裸环境下跳过（报告为 skipped 而非失败），ModDev {@code unitTest} 环境自动真跑。
 * 结果按 JVM 缓存（bootstrap 只试一次）。
 *
 * <p>ticket 39 起去掉 {@code >=26} 守卫：1.21.1 的 modification fixture 需要同一探测
 * （此前该文件只在 26.x 编译，1.21.1 侧从未需要注册表探测）。
 */
public final class VanillaRegistryProbe {

    private static volatile Boolean available;

    private VanillaRegistryProbe() {}

    public static boolean available() {
        Boolean result = available;
        if (result == null) {
            synchronized (VanillaRegistryProbe.class) {
                result = available;
                if (result == null) {
                    try {
                        Bootstrap.bootStrap();
                        // bootstrap 成功不代表注册表类初始化可用，再实际触碰一次
                        Class.forName("net.minecraft.world.item.Items", true,
                                VanillaRegistryProbe.class.getClassLoader());
                        result = true;
                    } catch (Throwable t) {
                        result = false;
                    }
                    available = result;
                }
            }
        }
        return result;
    }
}
