package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;

/**
 * 内置扩展点 {@code nekojs:script_properties}（ADR-0001 自包含 Point 文件）。
 *
 * <p><b>闭包特例</b>：累积器是 bootstrap 传入的 {@link ScriptPropertyRegistry}
 * （每轮值不同），故不提供静态常量，而由 {@link #point(ScriptPropertyRegistry)}
 * 静态工厂在注册期构造（ADR 预案：闭包点 = 静态工厂方法）。
 */
public final class ScriptPropertiesPoint {

    /** 扩展点 id（observable 契约）。 */
    public static final String ID = "nekojs:script_properties";

    private ScriptPropertiesPoint() {
    }

    /** 贡献面：实现本接口的插件被 {@code nekojs:script_properties} 扩展点收集。 */
    public interface Contributor extends NekoJSPlugin {

        /** 注册脚本属性（{@code AFTER}/{@code MODLOADED}/{@code DISABLE}/{@code PRIORITY} 等文件头属性）。 */
        default void registerScriptProperty(ScriptPropertyRegistry registry) {
        }
    }

    /** 扩展点定义（由 {@link NekoBuiltinPointsPlugin} 清单注册时构造）。 */
    public static NekoPluginExtensionPoint<Contributor, ScriptPropertyRegistry, ScriptPropertyRegistry> point(
            ScriptPropertyRegistry scriptProperties) {
        return NekoPluginExtensionPoint.<Contributor, ScriptPropertyRegistry, ScriptPropertyRegistry>builder(ID, Contributor.class)
                .merge(MergePolicy.append())
                .initializer(context -> scriptProperties)
                .collector(Contributor::registerScriptProperty)
                .finish(registry -> {
                    if (registry instanceof ScriptPropertyRegistry.Impl impl) {
                        impl.freeze();
                    }
                    return registry;
                })
                .build();
    }
}
