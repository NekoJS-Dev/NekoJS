package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;

/**
 * 扩展点提供者：让第三方插件定义自己的收集扩展点，与内置扩展点同批工作。
 *
 * <p>同时实现 {@link NekoJSPlugin} 与本接口的插件，会在 bootstrap 的扩展点注册阶段收到一次
 * {@link #registerPluginExtensionPoints} 回调，借此向 {@link NekoPluginExtensionRegistry}
 * 注册任意数量的 {@link NekoPluginExtensionPoint}（注册返回
 * {@link NekoPluginExtensionHandle}，bootstrap 完成后可凭它取回该点的 finisher 产物）。
 * 注册完成后（registry freeze），每个扩展点——内置与自定义一视同仁——都会按点优先执行序
 * 对本次 bootstrap 的<b>所有</b>插件逐个收集，包括定义该扩展点的插件自己。
 *
 * <p><b>生命周期：</b>注册窗口只在 {@link NekoPluginBootstrap} 的扩展点注册阶段开放：
 * bootstrap 先注册 14 个内置扩展点（{@code nekojs:script_compilers} … {@code nekojs:probe_backends}），
 * 再按插件列表顺序回调各 provider，然后 freeze。窗口随本次 bootstrap 关闭，
 * freeze 后再调用 {@link NekoPluginExtensionRegistry#register} 会抛出 {@link IllegalStateException}。
 *
 * <p><b>id 冲突：</b>扩展点 id 在同一次 bootstrap 内全局唯一——自定义 id 之间、
 * 以及自定义 id 与内置 {@code nekojs:*} id 冲突都在注册时立即抛出 {@link IllegalArgumentException}，
 * fail-fast 终止整个 bootstrap。
 *
 * <p><b>点优先执行序：</b>freeze 后按「扩展点 × 插件」逐点收集：每个扩展点依次
 * initializer 建累积器 → 按插件列表序（生产入口为 {@code @RegisterNekoJSPlugin}
 * priority 降序）遍历 collector → finisher 产不可变产物 → 发布（bootstrap context、
 * 注册句柄与结果容器）→ 下一个扩展点（扩展点按注册顺序，内置在前、自定义在后）。
 * 因此<b>扩展点间依赖 = 在 initializer / collector 里引用先注册点的产物</b>
 * （{@link NekoPluginExtensionContext#result}）：后注册点收集任意插件时，
 * 先注册点已经 finish，产物可直接取用。
 *
 * <p><b>与固有钩子的关系：</b>{@link NekoJSPlugin} 的固有钩子（{@code registerScriptCompilers}、
 * {@code registerBinding}、{@code registerEvents} 等 14 项）并非另一条特殊通道——它们就是
 * 同一机制下注册的内置扩展点（见 {@code BuiltinPluginExtensionPoints}）。
 * 第三方经本接口注册的扩展点与这些固有钩子地位完全平等、同批收集。
 */
public interface NekoPluginExtensionProvider extends NekoJSPlugin {

    /**
     * 向 bootstrap 注册本插件定义的扩展点。
     *
     * <p>仅在扩展点注册阶段被调用一次；此阶段内经
     * {@link NekoPluginExtensionRegistry#register} 注册的扩展点会在 freeze 后
     * 按点优先执行序对所有插件逐个收集（含本插件），注册返回的
     * {@link NekoPluginExtensionHandle} 在该点 finish 后可取回产物。
     * 窗口关闭后再注册抛出 {@link IllegalStateException}，
     * id 重复抛出 {@link IllegalArgumentException}。
     *
     * @param registry bootstrap 提供的注册器（仅本回调期间可写），
     *                 {@code register} 返回的句柄可在 bootstrap 完成后取回产物
     */
    void registerPluginExtensionPoints(NekoPluginExtensionRegistry registry);
}
