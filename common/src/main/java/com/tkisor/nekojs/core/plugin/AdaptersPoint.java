package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.data.JSTypeAdapterRegistry;

import java.util.List;

/**
 * 内置扩展点 {@code nekojs:adapters}（ADR-0001 自包含 Point 文件）。
 * merge 策略 {@link MergePolicy#append}（适配器列表收集）；累积器即
 * {@link JSTypeAdapterRegistry.Impl}（registry 形态）。
 */
public final class AdaptersPoint {

    /** 扩展点 id（observable 契约）。 */
    public static final String ID = "nekojs:adapters";

    private AdaptersPoint() {
    }

    /** 显式声明形态（与直接覆写 NekoJSPlugin 对应钩子等价收集）：插件被 {@code nekojs:adapters} 扩展点收集。 */
    public interface Contributor extends NekoJSPlugin {

        /**
         * 注册 JS↔Java 类型适配器（{@code string → ItemStack} 等参数自动转换）。
         * 适配器同时驱动 probe 的输入别名（{@code $ItemStack_}）生成。
         */
        default void registerAdapters(JSTypeAdapterRegistry registry) {
        }
    }

    /** 扩展点定义（由 {@link NekoBuiltinPointsPlugin} 清单注册）。 */
    public static final NekoPluginExtensionPoint<NekoJSPlugin, JSTypeAdapterRegistry, List<Object>> POINT =
            NekoPluginExtensionPoint.<NekoJSPlugin, JSTypeAdapterRegistry, List<Object>>builder(ID, NekoJSPlugin.class)
                    .merge(MergePolicy.append())
                    .initializer(context -> new JSTypeAdapterRegistry.Impl())
                    .collector(NekoJSPlugin::registerAdapters)
                    .finish(registry -> List.copyOf(registry.view()))
                    .build();
}
