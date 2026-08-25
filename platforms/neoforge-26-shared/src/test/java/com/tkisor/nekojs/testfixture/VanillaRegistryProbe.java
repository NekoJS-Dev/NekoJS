package com.tkisor.nekojs.testfixture;

import net.minecraft.server.Bootstrap;

/**
 * 裸 JUnit 能否初始化 vanilla 注册表的探测（26-shared 测试树共享）。
 *
 * <p>无 FML Loader 的 JVM 里 {@code BuiltInRegistries}/{@code Items}/{@code DataComponents}
 * 的类初始化链会抛错（26.x 还要求先 {@code Bootstrap.bootStrap()}，而 bootstrap 自身
 * 依赖 FML Loader）。涉及这些类的测试用 {@code Assumptions.assumeTrue(available())} 守卫：
 * 裸环境下跳过（报告为 skipped 而非失败），将来接入 ModDev {@code unitTest} 后自动真跑。
 * 结果按 JVM 缓存（bootstrap 只试一次）。
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
