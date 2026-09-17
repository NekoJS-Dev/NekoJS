// TODO(fabric): fabric 侧还没有对应实现，整文件守卫等移植完成后去掉
//? if neoforge {
//? if >=26 {
package com.tkisor.nekojs.dynamic;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.data.BindingRegistry;
import com.tkisor.nekojs.api.event.EventGroupRegistry;
import com.tkisor.nekojs.core.dynamic.facade.DynamicRegistryEvents;
import com.tkisor.nekojs.core.dynamic.plan.DynamicBuilderSurfaces;
import com.tkisor.nekojs.core.plugin.EventsPoint;
import com.tkisor.nekojs.core.plugin.TypeDocsPoint;

/**
 * Standalone plugin exposing the {@code DynamicRegistry} binding to SERVER
 * scripts (NeoForge 26.1/26.2, via neoforge-26-shared).
 *
 * <p>The binding object is a stateless view over {@link DynamicRegistries};
 * per-reload behavior (stale-retain claims) hangs off the binding's
 * {@code close(ScriptType)} hook, see {@link DynamicRegistryBinding}.
 *
 * <p>ticket 16 additionally registers the runtime dynamic-registry event facade
 * ({@link DynamicRegistryEvents#GROUP}: inert candidate plans, typed callback
 * builders — a lifecycle fully separate from this legacy direct-registration
 * binding and from startup {@code RegistryEvents}) plus its candidate domain
 * collector ({@link DynamicRegistryFacade#bootstrap()}) and the derived
 * dynamic-builder declaration entries.
 */
@RegisterNekoJSPlugin
public final class DynamicRegistryPlugin implements NekoJSPlugin,
        com.tkisor.nekojs.core.plugin.BindingsPoint.Contributor,
        EventsPoint.Contributor,
        TypeDocsPoint.Contributor {

    @Override
    public void registerBinding(BindingRegistry registry) {
        if (registry.scriptType() == ScriptType.SERVER) {
            registry.register(new DynamicRegistryBinding());
        }
    }

    @Override
    public void registerEvents(EventGroupRegistry registry) {
        // 服务器运行期动态注册事件 facade（ticket 16）：inert 候选计划，与旧直注 binding、
        // 启动期 RegistryEvents 的生命周期完全分离。bootstrap 幂等（收集器只挂一次）。
        registry.register(DynamicRegistryEvents.GROUP);
        DynamicRegistryFacade.bootstrap();
    }

    @Override
    public void registerTypeDocs(com.tkisor.nekojs.core.plugin.TypeDocsRegister registry) {
        // 动态定义 builder 的契约派生条目（ticket 16 AC9）：与 runtime member/fingerprint
        // 同一反射输入，进 probe TS/Python declaration（@registry-builders 面）
        DynamicBuilderSurfaces.derive().forEach(registry::registerRegistryBuilderSurface);
    }
}
//?}
//?}
