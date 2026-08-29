package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;

public interface NekoPluginExtensionRegistry {
    /**
     * 注册一个扩展点，返回可在 bootstrap 完成后取回产物的 {@link NekoPluginExtensionHandle}。
     *
     * @throws IllegalStateException 注册窗口已关闭（registry freeze 后）
     * @throws IllegalArgumentException 同一次 bootstrap 内扩展点 id 重复
     */
    <P extends NekoJSPlugin, A, R> NekoPluginExtensionHandle<R> register(NekoPluginExtensionPoint<P, A, R> extensionPoint);
}
