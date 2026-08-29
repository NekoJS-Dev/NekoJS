package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.data.BindingRegistry;
import com.tkisor.nekojs.script.ScriptTypedValue;

import java.util.Map;

/**
 * 内置扩展点 {@code nekojs:bindings}（ADR-0001 自包含 Point 文件）。
 *
 * <p><b>闭包特例</b>：collector 按 client 环境过滤 ScriptType（专用服务器不收集
 * CLIENT 绑定），谓词捕获每轮 bootstrap 的 client 值——由
 * {@link #point(boolean)} 静态工厂在注册期构造。
 *
 * <p>累积器为按 {@link ScriptType} 惰性创建的 {@link BindingRegistry}；
 * finisher 冻结为 {@code ScriptType → (name → Binding)} 快照。
 */
public final class BindingsPoint {

    /** 扩展点 id（observable 契约）。 */
    public static final String ID = "nekojs:bindings";

    private BindingsPoint() {
    }

    /** 贡献面：实现本接口的插件被 {@code nekojs:bindings} 扩展点收集。 */
    public interface Contributor extends NekoJSPlugin {

        /**
         * 注册全局绑定（脚本可直接引用的全局名，如 {@code Item}/{@code Ingredient}）。
         * 绑定可以是 Java 类、实例或 {@code Binding.of(...)} 显式声明值类型（供 preflight 校验）。
         */
        default void registerBinding(BindingRegistry registry) {
        }
    }

    /** 扩展点定义（由 {@link NekoBuiltinPointsPlugin} 清单注册时构造）。 */
    public static NekoPluginExtensionPoint<Contributor, ScriptTypedValue<BindingRegistry>, Map<ScriptType, Map<String, Binding>>> point(
            boolean client) {
        var predicate = NekoPluginBootstrap.bindingPredicate(client);
        return NekoPluginExtensionPoint.<Contributor, ScriptTypedValue<BindingRegistry>, Map<ScriptType, Map<String, Binding>>>builder(ID, Contributor.class)
                .merge(MergePolicy.append())
                .initializer(context -> ScriptTypedValue.of(BindingRegistry.BindingRegistryImpl::new))
                .collector((plugin, registries) -> predicate.streamMatched()
                        .map(registries::at)
                        .forEach(plugin::registerBinding))
                .finish(NekoPluginBootstrap::freezeBindings)
                .build();
    }
}
