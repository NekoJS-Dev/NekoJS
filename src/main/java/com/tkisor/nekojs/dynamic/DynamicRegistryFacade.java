// TODO(fabric): fabric 侧还没有对应实现，整文件守卫等移植完成后去掉
//? if neoforge {
//? if >=26 {
package com.tkisor.nekojs.dynamic;

import com.tkisor.nekojs.core.dynamic.facade.DynamicRegistryEvents;
import com.tkisor.nekojs.core.dynamic.facade.DynamicRegistryFacadeRuntime;

/**
 * 动态注册 facade 的生产装配（ticket 16）：进程级持有 {@link DynamicRegistryFacadeRuntime}
 * 单例；候选域收集器由平台装配（{@code NekoJSMod}）经
 * {@code NekoRuntimeRoot#registerDomainCollector} 注册进 root（与票 39 的 modification
 * owner 同一接缝），reload 的 DOMAIN_PLAN 阶段统一调用；server registry ready 时由
 * {@code ServerEventListener#onServerAboutToStart} 触发初次候选。
 *
 * <p>旧 {@code DynamicRegistry} 静态绑定（{@link DynamicRegistryJS} 直注路径）保持不动：
 * 两条路径各自独立记账，旧路径的删除需维护者 sign-off（本票 AC11 不勾选）。
 */
public final class DynamicRegistryFacade {

    private static final DynamicRegistryFacadeRuntime RUNTIME = new DynamicRegistryFacadeRuntime();

    private DynamicRegistryFacade() {
    }

    /** 进程级 facade runtime（平台装配时注册进 root；测试经构造隔离实例）。 */
    public static DynamicRegistryFacadeRuntime runtime() {
        return RUNTIME;
    }

    /**
     * 初次候选触发（server registry ready）：向 active 总线投递收集事件并按
     * preflight/publish 语义处置。幂等（同定义重声明不冲突），与 reload 候选收集
     * 消费同一账本。
     */
    public static void fireInitialCollection() {
        RUNTIME.collectInitial("server-registry-ready");
    }
}
//?}
//?}
