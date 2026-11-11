// TODO(fabric): fabric 侧还没有对应实现，整文件守卫等移植完成后去掉
//? if neoforge {
package com.tkisor.nekojs.core;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.utils.ReflectionUtils;

/**
 * 平台插件加载器：仅负责「发现」被 {@link RegisterNekoJSPlugin} 标记的类，
 * 过滤/实例化/priority 排序交给 common 的 {@link NekoJSBasePluginManager#registerClass}。
 */
public final class NeoForgePluginLoader {
    private NeoForgePluginLoader() {}

    private static void registerOwnedClass(Class<?> plugin) {
        var owners = net.neoforged.fml.ModList.get().getAllScanData().stream()
                .filter(data -> data.getClasses().stream().anyMatch(entry ->
                        entry.clazz().getClassName().equals(plugin.getName())))
                .flatMap(data -> data.getIModInfoData().stream())
                .flatMap(info -> info.getMods().stream())
                .map(info -> info.getModId()).distinct().toList();
        if (owners.size() == 1) {
            NekoJSBasePluginManager.registerClass(owners.getFirst(), plugin);
        } else {
            // Multi-mod jars require an explicit owner for module publications.
            NekoJSBasePluginManager.registerClass(plugin);
        }
    }

    public static void loadAnnotatedPlugins() {
        ReflectionUtils.findAnnotationClasses(
                RegisterNekoJSPlugin.class,
                null,
                NeoForgePluginLoader::registerOwnedClass,
                () -> NekoJS.LOGGER.debug("Plugin scan finished")
        );
    }
}
//?}
