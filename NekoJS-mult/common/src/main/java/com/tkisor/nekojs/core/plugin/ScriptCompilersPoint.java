package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;

/**
 * 内置扩展点 {@code nekojs:script_compilers}（ADR-0001 自包含 Point 文件）。
 * 累积器即 {@link ScriptCompilerRegistry}（registry 形态）；merge append。
 */
public final class ScriptCompilersPoint {

    /** 扩展点 id（observable 契约）。 */
    public static final String ID = "nekojs:script_compilers";

    private ScriptCompilersPoint() {
    }

    /** 贡献面：实现本接口的插件被 {@code nekojs:script_compilers} 扩展点收集。 */
    public interface Contributor extends NekoJSPlugin {

        /** 注册脚本编译器（语言插件）。编译器在脚本加载/热重载时被调用。 */
        default void registerScriptCompilers(ScriptCompilerRegistry registry) {
        }
    }

    /** 扩展点定义（由 {@link NekoBuiltinPointsPlugin} 清单注册）。 */
    public static final NekoPluginExtensionPoint<Contributor, ScriptCompilerRegistry, ScriptCompilerRegistry> POINT =
            NekoPluginExtensionPoint.<Contributor, ScriptCompilerRegistry, ScriptCompilerRegistry>builder(ID, Contributor.class)
                    .merge(MergePolicy.append())
                    .initializer(context -> ScriptCompilerRegistry.createRuntimeRegistry())
                    .collector(Contributor::registerScriptCompilers)
                    .finish(registry -> {
                        registry.freeze();
                        return registry;
                    })
                    .build();
}
