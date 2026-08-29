package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link EventBusJS#hasListeners()} 的兜底分支（C5）。
 *
 * <p>{@code hasListeners()} 先查 {@code tokensByType} 镜像（JS 侧经 {@link EventBusJS#execute}
 * 注册的监听器）；为防绕过 {@code execute} 的直接 Java 注册（测试、bridge 代码）失同步，
 * 再兜底检查底层 bus（{@code EventBusBase.isEmpty()}）。本测试用
 * {@link EventBusJS#bus()} 直接注册 Java 监听器——此场景 {@code tokensByType} 恒为空，
 * 正是要覆盖的兜底分支。
 *
 * <p>{@link EventBusJS} 按 {@code ScriptType} 分桶管理监听器（注册 / 清理路径触发
 * {@code ScriptType.<clinit>}，依赖 {@code Platform} 已初始化），故 {@code @BeforeAll}
 * 先做平台 fixture 初始化（与 {@code TypeScriptNoopIrGoldenTest} 同款），保证不依赖测试执行顺序。
 */
class EventBusJSHasListenersTest {

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    /** 无任何监听器（镜像与底层 bus 都空）→ false。 */
    @Test
    void hasListenersIsFalseWithoutAnyListener() {
        EventBusJS<String, Void> bus = EventBusJS.of(String.class);
        assertFalse(bus.hasListeners());
    }

    /**
     * 经 {@code bus().listen(Consumer)} 直接注册 Java 监听器（绕过 {@code execute()}，
     * {@code tokensByType} 镜像为空）→ {@code EventBusBase.isEmpty()} 兜底分支必须报 true。
     */
    @Test
    void hasListenersIsTrueForDirectJavaListener() {
        EventBusJS<String, Void> bus = EventBusJS.of(String.class);
        Consumer<String> listener = ignored -> { };
        bus.bus().listen(listener);
        assertTrue(bus.hasListeners(), "Java 直接注册的监听器应被 isEmpty() 兜底分支发现");
    }

    /** 兜底分支与底层 bus 状态保持同步：注销后回到 false。 */
    @Test
    void hasListenersReturnsFalseAfterDirectListenerUnregistered() {
        EventBusJS<String, Void> bus = EventBusJS.of(String.class);
        EventListenerToken<String> token = bus.bus().listen(ignored -> { });
        assertTrue(bus.hasListeners());
        assertTrue(bus.bus().unregister(token), "unregister 应成功");
        assertFalse(bus.hasListeners());
    }
}
