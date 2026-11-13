// TODO(fabric): there is no Fabric implementation yet; remove this whole-file guard once the port is done
//? if neoforge {
package com.tkisor.nekojs.core;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.utils.ReflectionUtils;

/**
 * Platform plugin loader. It only discovers classes marked with {@link RegisterNekoJSPlugin};
 * filtering, instantiation and priority ordering are left to
 * {@link NekoJSBasePluginManager#registerClass} in common.
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
