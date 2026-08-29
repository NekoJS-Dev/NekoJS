package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;

/**
 * 内置点定义插件（ADR-0003）：以与第三方完全相同的 {@link NekoPluginExtensionProvider}
 * 路径注册全部内置扩展点，本清单即内置扩展点的<b>显式总索引</b>——新增内置扩展点
 * = 一个自包含 Point 文件 + 本清单一行。
 *
 * <p><b>内置先行相位：</b>bootstrap 在第三方 provider 回调之前显式调用本插件
 * （引擎对依赖图根部的保证，见 {@code NekoPluginBootstrap#collect}）——不依赖
 * 插件 priority，第三方不可能抢占。本类不经 {@code @RegisterNekoJSPlugin} 扫描
 * 发现，由 bootstrap 直接构造，正是"显式提升"的实现形态。
 *
 * <p>闭包特例（{@code script_properties} / {@code bindings}）经构造参数带入每轮
 * bootstrap 的值，注册期以静态工厂构造扩展点。
 */
public final class NekoBuiltinPointsPlugin implements NekoJSPlugin, NekoPluginExtensionProvider {

    private final ScriptPropertyRegistry scriptProperties;
    private final boolean client;

    public NekoBuiltinPointsPlugin(ScriptPropertyRegistry scriptProperties, boolean client) {
        this.scriptProperties = scriptProperties;
        this.client = client;
    }

    @Override
    public void registerPluginExtensionPoints(NekoPluginExtensionRegistry registry) {
        registry.register(ScriptCompilersPoint.POINT);
        registry.register(ScriptPropertiesPoint.point(scriptProperties));
        registry.register(BindingsPoint.point(client));
        registry.register(AdaptersPoint.POINT);
        registry.register(TypeDocsPoint.POINT);
        registry.register(NodeTypeDocsPoint.POINT);
        registry.register(NodeModulesPoint.POINT);
        registry.register(EventsPoint.POINT);
        registry.register(ClientEventsPoint.POINT);
        registry.register(RecipeNamespacesPoint.POINT);
        registry.register(RecipeSchemasPoint.POINT);
        registry.register(RecipeLifecyclePoint.POINT);
        registry.register(LifecyclePoint.POINT);
        registry.register(ProbeBackendsPoint.POINT);
    }
}
