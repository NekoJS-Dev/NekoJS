package com.tkisor.nekojs.core;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.platform.Platform;

import java.lang.reflect.Modifier;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 负责管理所有 NekoJS 基础插件。
 *
 * <p>平台 PluginLoader 仅负责「发现」被 {@link RegisterNekoJSPlugin} 标记的类，
 * 通过 {@link #registerClass(Class)} 交给本类完成「按 clientOnly/requiredMods 过滤 + 实例化 + priority 排序」。
 * {@link #getPlugins()} 返回按 priority 降序（数值大先）排列的视图，priority 相同时保持登记顺序。
 */
public final class NekoJSBasePluginManager {
    private record PluginEntry(NekoJSPlugin plugin, int priority) {}

    private static final List<PluginEntry> ENTRIES = new CopyOnWriteArrayList<>();
    private static volatile List<NekoJSPlugin> sortedView = List.of();

    private NekoJSBasePluginManager() {}

    /**
     * 读取类上的 {@link RegisterNekoJSPlugin} 注解，按 {@code clientOnly}/{@code requiredMods}
     * 过滤后实例化并登记。priority 来自注解（缺省 1000）。
     */
    public static void registerClass(Class<?> clazz) {
        if (!NekoJSPlugin.class.isAssignableFrom(clazz)) {
            NekoJS.LOGGER.error("Plugin {} does not implement NekoJSPlugin", clazz.getName());
            return;
        }
        int mod = clazz.getModifiers();
        if (clazz.isInterface() || Modifier.isAbstract(mod)) {
            NekoJS.LOGGER.error("Plugin {} is not a concrete class", clazz.getName());
            return;
        }

        RegisterNekoJSPlugin anno = clazz.getAnnotation(RegisterNekoJSPlugin.class);
        if (anno != null) {
            if (anno.clientOnly() && !Platform.isClient()) {
                NekoJS.LOGGER.debug("Skip client-only plugin {} on dedicated server", clazz.getName());
                return;
            }
            for (String required : anno.requiredMods()) {
                if (!Platform.isLoaded(required)) {
                    NekoJS.LOGGER.debug("Skip plugin {} (missing required mod {})", clazz.getName(), required);
                    return;
                }
            }
        }

        try {
            NekoJSPlugin plugin = (NekoJSPlugin) clazz.getDeclaredConstructor().newInstance();
            int priority = anno != null ? anno.priority() : 1000;
            ENTRIES.add(new PluginEntry(plugin, priority));
            sortedView = null; // 失效排序缓存
            NekoJS.LOGGER.debug("Registered plugin: {} (priority {})", clazz.getName(), priority);
        } catch (Throwable t) {
            NekoJS.LOGGER.error("Failed to instantiate plugin {}", clazz.getName(), t);
        }
    }

    /** 按 priority 降序（数值大先）返回所有已登记插件。 */
    public static List<NekoJSPlugin> getPlugins() {
        List<NekoJSPlugin> view = sortedView;
        if (view != null) return view;
        view = ENTRIES.stream()
                .sorted(Comparator.comparingInt(PluginEntry::priority).reversed())
                .map(PluginEntry::plugin)
                .toList();
        sortedView = view;
        return view;
    }
}
