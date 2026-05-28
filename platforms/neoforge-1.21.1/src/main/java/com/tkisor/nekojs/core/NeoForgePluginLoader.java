package com.tkisor.nekojs.core;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.utils.ReflectionUtils;

import java.lang.reflect.Modifier;

public final class NeoForgePluginLoader {
    private NeoForgePluginLoader() {}

    public static void loadAnnotatedPlugins() {
        ReflectionUtils.findAnnotationClasses(
                RegisterNekoJSPlugin.class,
                null,
                NeoForgePluginLoader::registerPluginClass,
                () -> NekoJS.LOGGER.debug("[NekoJS] Plugin scan finished")
        );
    }

    private static void registerPluginClass(Class<?> clazz) {
        if (!NekoJSPlugin.class.isAssignableFrom(clazz)) {
            NekoJS.LOGGER.error("[NekoJS] Plugin {} does not implement NekoJSPlugin", clazz.getName());
            return;
        }

        int mod = clazz.getModifiers();
        if (clazz.isInterface() || Modifier.isAbstract(mod)) {
            NekoJS.LOGGER.error("[NekoJS] Plugin {} is not a concrete class", clazz.getName());
            return;
        }

        try {
            NekoJSPlugin plugin = (NekoJSPlugin) clazz.getDeclaredConstructor().newInstance();
            NekoJSPluginManager.register(plugin);
            NekoJS.LOGGER.debug("[NekoJS] Registered plugin: {}", clazz.getName());
        } catch (Throwable t) {
            NekoJS.LOGGER.error("[NekoJS] Failed to instantiate plugin {}", clazz.getName(), t);
        }
    }
}
