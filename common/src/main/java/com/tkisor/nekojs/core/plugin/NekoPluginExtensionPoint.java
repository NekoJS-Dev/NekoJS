package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * 一个收集扩展点的完整定义：id + 目标插件类型 + 环境谓词 + 收集回调。
 *
 * <p>扩展点由 {@link NekoPluginExtensionProvider} 在 bootstrap 的 collectExtensions 阶段
 * 经 {@link NekoPluginExtensionRegistry#register} 注册（{@code NekoJSPlugin} 的 14 个固有钩子
 * 也是这样注册的内置扩展点，id 为 {@code nekojs:*}）。registry freeze 后按
 * 「插件 × 扩展点」双层循环收集：外层插件按传入 bootstrap 的列表顺序
 * （生产入口为 {@code @RegisterNekoJSPlugin} priority 降序），内层扩展点按注册顺序
 * （内置在前、自定义在后）。
 *
 * @param id         扩展点 id，同一次 bootstrap 内全局唯一（含与内置 {@code nekojs:*} 冲突，
 *                   重复注册抛 {@link IllegalArgumentException}）；第三方建议使用
 *                   {@code modid:name} 命名空间前缀。不允许为 null 或 blank。
 * @param pluginType 目标插件类型过滤器：仅 {@code pluginType.isInstance(plugin)} 的插件会被收集，
 *                   其余插件直接跳过、collector 不被调用
 * @param enabled    环境谓词：每轮收集前以当前 {@link NekoPluginExtensionContext} 测试，
 *                   为 {@code false} 时整个扩展点在当前环境（如专用服务器）跳过
 * @param collector  收集回调：仅对同时通过环境谓词与类型过滤的插件调用一次
 * @param <P>        目标插件类型（{@link NekoJSPlugin} 的子类型）
 */
public record NekoPluginExtensionPoint<P extends NekoJSPlugin>(
        String id,
        Class<P> pluginType,
        Predicate<NekoPluginExtensionContext> enabled,
        BiConsumer<P, NekoPluginExtensionContext> collector
) {
    public NekoPluginExtensionPoint {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Plugin extension point id must not be blank");
        }
        Objects.requireNonNull(pluginType, "pluginType");
        Objects.requireNonNull(enabled, "enabled");
        Objects.requireNonNull(collector, "collector");
    }

    /**
     * 全环境收集的便捷工厂：{@code enabled} 恒为 {@code true}，
     * 客户端与专用服务器进程上都会收集。
     */
    public static <P extends NekoJSPlugin> NekoPluginExtensionPoint<P> of(
            String id,
            Class<P> pluginType,
            BiConsumer<P, NekoPluginExtensionContext> collector
    ) {
        return new NekoPluginExtensionPoint<>(id, pluginType, context -> true, collector);
    }

    /**
     * 仅客户端收集的便捷工厂：{@code enabled = context.client()}（见
     * {@link NekoPluginExtensionContext#client()}）。专用服务器进程上整个扩展点跳过
     * （与内置 {@code nekojs:client_events} 的行为一致）。
     */
    public static <P extends NekoJSPlugin> NekoPluginExtensionPoint<P> clientOnly(
            String id,
            Class<P> pluginType,
            BiConsumer<P, NekoPluginExtensionContext> collector
    ) {
        return new NekoPluginExtensionPoint<>(id, pluginType, NekoPluginExtensionContext::client, collector);
    }

    /**
     * 对单个插件执行一次收集。短路语义：环境谓词 {@code enabled} 不通过、
     * 或插件不是 {@code pluginType} 的实例时直接返回，collector 不被调用。
     */
    public void collect(NekoJSPlugin plugin, NekoPluginExtensionContext context) {
        if (enabled.test(context) && pluginType.isInstance(plugin)) {
            collector.accept(pluginType.cast(plugin), context);
        }
    }
}
