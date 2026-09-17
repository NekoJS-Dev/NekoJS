// TODO(fabric): fabric 侧还没有对应实现，整文件守卫等移植完成后去掉
//? if neoforge {
//? if >=26 {
package com.tkisor.nekojs.dynamic;

import com.tkisor.nekojs.core.dynamic.facade.DynamicRegistryEvents;
import com.tkisor.nekojs.core.dynamic.facade.DynamicRegistryFacadeRuntime;
import com.tkisor.nekojs.script.ScriptManager;

/**
 * 动态注册 facade 的生产装配（ticket 16）：进程级持有 {@link DynamicRegistryFacadeRuntime}
 * 单例，bootstrap 时把事件组交给插件事件注册、把候选域收集器挂进 reload 管线
 * （{@link ScriptManager#registerCandidateDomainCollector}），server registry ready
 * 时由 {@code ServerEventListener#onServerAboutToStart} 触发初次候选。
 *
 * <p>旧 {@code DynamicRegistry} 静态绑定（{@link DynamicRegistryJS} 直注路径）保持不动：
 * 两条路径各自独立记账，旧路径的删除需维护者 sign-off（本票 AC11 不勾选）。
 */
public final class DynamicRegistryFacade {

    private static final DynamicRegistryFacadeRuntime RUNTIME = new DynamicRegistryFacadeRuntime();
    private static boolean bootstrapped;

    private DynamicRegistryFacade() {
    }

    /** 进程级 facade runtime（测试经构造 {@link DynamicRegistryFacadeRuntime} 隔离实例）。 */
    public static DynamicRegistryFacadeRuntime runtime() {
        return RUNTIME;
    }

    /**
     * bootstrap 接线（{@link DynamicRegistryPlugin#registerEvents} 调用，幂等）：
     * 注册候选域收集器。事件组由插件经 {@link DynamicRegistryEvents#GROUP} 注册。
     */
    static synchronized void bootstrap() {
        if (bootstrapped) {
            return;
        }
        bootstrapped = true;
        ScriptManager.registerCandidateDomainCollector(RUNTIME);
    }

    /**
     * 初次候选触发（server registry ready）：向 active 总线投递收集事件并按
     * preflight/publish 语义处置。幂等（同定义重声明不冲突），与 reload 候选收集
     * 消费同一账本。
     */
    public static void fireInitialCollection() {
        bootstrap();
        RUNTIME.collectInitial("server-registry-ready");
    }
}
//?}
//?}
