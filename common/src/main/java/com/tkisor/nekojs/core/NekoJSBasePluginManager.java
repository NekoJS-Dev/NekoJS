package com.tkisor.nekojs.core;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.api.plugin.OwnedPlugin;
import com.tkisor.nekojs.api.plugin.PluginIdentity;
import com.tkisor.nekojs.platform.Platform;

import java.lang.reflect.Modifier;
import java.net.URI;
import java.security.ProtectionDomain;
import java.util.ArrayList;
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
    private record PluginEntry(PluginIdentity identity, NekoJSPlugin plugin, int priority) {}

    // non-final ONLY as a private test seam (tests reset/inject this list); do not reassign in production code.
    private static List<PluginEntry> ENTRIES = new CopyOnWriteArrayList<>();
    private static volatile List<NekoJSPlugin> sortedView = null;
    private static volatile List<OwnedPlugin> ownedView = null;

    private NekoJSBasePluginManager() {}

    /**
     * 读取类上的 {@link RegisterNekoJSPlugin} 注解，按 {@code clientOnly}/{@code requiredMods}
     * 过滤后实例化并登记。priority 来自注解（缺省 1000）。
     *
     * <p>使用 legacy owner 格式 {@code "legacy:<class FQN>"}，codeSource 来自 protection domain。
     */
    public static synchronized void registerClass(Class<?> clazz) {
        PluginIdentity identity = createLegacyIdentity(clazz);
        registerClass(identity, clazz);
    }

    /**
     * 使用已验证的 owner identity 注册插件类。
     */
    public static synchronized void registerClass(PluginIdentity identity, Class<?> clazz) {
        if (!NekoJSPlugin.class.isAssignableFrom(clazz)) {
            NekoJS.LOGGER.error("Plugin {} does not implement NekoJSPlugin", clazz.getName());
            return;
        }
        int mod = clazz.getModifiers();
        if (clazz.isInterface() || Modifier.isAbstract(mod)) {
            NekoJS.LOGGER.error("Plugin {} is not a concrete class", clazz.getName());
            return;
        }

        // 同 (identity, class) 的重复发现只登记一次：dev classpath 可能重复列出同一 jar
        // （cleanroom run 里 common/common-api 各出现两次，getResources 会上报两遍），
        // NeoForge 的 ModList scan data 同样可能对同一类给出多条 AnnotationData。
        // 不同 identity（不同 owner）注册同一类仍是允许的语义。
        for (PluginEntry entry : ENTRIES) {
            if (entry.plugin().getClass() == clazz && entry.identity().equals(identity)) {
                NekoJS.LOGGER.debug("Skip duplicate plugin registration: {} (owner {})", clazz.getName(), identity.ownerId());
                return;
            }
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
            ENTRIES.add(new PluginEntry(identity, plugin, priority));
            sortedView = null;
            ownedView = null;
            NekoJS.LOGGER.debug("Registered plugin: {} (priority {}, owner {})", clazz.getName(), priority, identity.ownerId());
        } catch (Throwable t) {
            NekoJS.LOGGER.error("Failed to instantiate plugin {}", clazz.getName(), t);
        }
    }

    private static PluginIdentity createLegacyIdentity(Class<?> clazz) {
        URI codeSource = codeSourceUri(clazz);
        return new PluginIdentity("legacy:" + clazz.getName(), clazz.getName(), codeSource);
    }

    private static URI codeSourceUri(Class<?> clazz) {
        try {
            ProtectionDomain pd = clazz.getProtectionDomain();
            if (pd != null && pd.getCodeSource() != null && pd.getCodeSource().getLocation() != null) {
                return pd.getCodeSource().getLocation().toURI();
            }
        } catch (Exception ignored) {
        }
        return URI.create("legacy:" + clazz.getName());
    }

    /** 按 priority 降序（数值大先）返回所有已登记插件。 */
    public static synchronized List<NekoJSPlugin> getPlugins() {
        List<NekoJSPlugin> view = sortedView;
        if (view != null) return view;
        view = ENTRIES.stream()
                .sorted(Comparator.comparingInt(PluginEntry::priority).reversed())
                .map(PluginEntry::plugin)
                .toList();
        sortedView = view;
        return view;
    }

    /** 返回所有已登记插件及其 owner identity，按 priority 降序排列。 */
    public static synchronized List<OwnedPlugin> getOwnedPlugins() {
        List<OwnedPlugin> view = ownedView;
        if (view != null) return view;
        view = ENTRIES.stream()
                .sorted(Comparator.comparingInt(PluginEntry::priority).reversed())
                .map(entry -> new OwnedPlugin(entry.identity(), entry.plugin()))
                .toList();
        ownedView = view;
        return view;
    }
}
