package com.tkisor.nekojs.core;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.utils.ReflectionUtils;

/**
 * 平台插件加载器：仅负责「发现」被 {@link RegisterNekoJSPlugin} 标记的类，
 * 过滤/实例化/priority 排序交给 common 的 {@link NekoJSBasePluginManager#registerClass}。
 */
public final class ForgePluginLoader {
    private ForgePluginLoader() {}

    public static void loadAnnotatedPlugins() {
        ReflectionUtils.findAnnotationClasses(
                RegisterNekoJSPlugin.class,
                null,
                NekoJSBasePluginManager::registerClass,
                () -> NekoJS.LOGGER.debug("Plugin scan finished")
        );
    }
}
