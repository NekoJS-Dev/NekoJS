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
 * Manages all NekoJS base plugins.
 *
 * <p>The platform PluginLoader only discovers classes marked with {@link RegisterNekoJSPlugin}
 * and hands them to {@link #registerClass(Class)}, which filters them by
 * clientOnly/requiredMods, instantiates them and orders them by priority. {@link #getPlugins()}
 * returns a view sorted by priority from highest to lowest; equal priorities fall back to the
 * implementation class FQN in lexicographic order, because registration order comes from the
 * platform annotation scan and is not deterministic.
 */
public final class NekoJSBasePluginManager {
    private record PluginEntry(PluginIdentity identity, NekoJSPlugin plugin, int priority) {}

    // All three fields are non-final only so tests can reset or inject ENTRIES and clear
    // both derived views together; do not reassign them in production code.
    private static List<PluginEntry> ENTRIES = new CopyOnWriteArrayList<>();
    private static volatile List<NekoJSPlugin> sortedView = null;
    private static volatile List<OwnedPlugin> ownedView = null;

    private NekoJSBasePluginManager() {}

    /**
     * Reads the {@link RegisterNekoJSPlugin} annotation on the class, filters it by
     * {@code clientOnly}/{@code requiredMods}, then instantiates and registers the plugin.
     * priority comes from the annotation (default 1000).
     *
     * <p>Uses the legacy owner format {@code "legacy:<class FQN>"}; codeSource comes from the
     * protection domain.
     */
    public static synchronized void registerClass(Class<?> clazz) {
        PluginIdentity identity = createLegacyIdentity(clazz);
        registerClass(identity, clazz);
    }

    /**
     * Registers a plugin class under an owner identity that has already been validated.
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

        // The same (identity, class) pair is registered only once: the dev classpath can list
        // the same jar twice (common/common-api may each appear twice and getResources reports
        // them twice), and NeoForge's ModList scan data can likewise return several
        // AnnotationData for one class. Registering the same class under a different identity
        // (a different owner) stays allowed.
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

    /** Registers a discovered class using the loader-provided mod identity. */
    public static synchronized void registerClass(String ownerId, Class<?> clazz) {
        registerClass(new PluginIdentity(ownerId, clazz.getName(), codeSourceUri(clazz)), clazz);
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

    /**
     * Plugin order: priority from highest to lowest, then the implementation class FQN, then the
     * owner id, both lexicographic.
     *
     * <p>Priority alone is not enough: 7 of the 8 built-in plugins use the default 1000, and
     * registration order comes from the platform annotation scan (NeoForge's
     * {@code getAllScanData()} is a Set, so iteration order is not guaranteed) while binding
     * registration is first-wins. Relying on registration order within one priority would let
     * "whose binding wins" change between JVM runs. The FQN tie-break makes the order fully
     * deterministic on the same classpath.
     */
    private static final Comparator<PluginEntry> ORDER = Comparator
            .comparingInt(PluginEntry::priority).reversed()
            .thenComparing(entry -> entry.plugin().getClass().getName())
            .thenComparing(entry -> entry.identity().ownerId());

    /** Returns every registered plugin, sorted by priority from highest to lowest, then by class FQN. */
    public static synchronized List<NekoJSPlugin> getPlugins() {
        List<NekoJSPlugin> view = sortedView;
        if (view != null) return view;
        view = ENTRIES.stream()
                .sorted(ORDER)
                .map(PluginEntry::plugin)
                .toList();
        sortedView = view;
        return view;
    }

    /** Returns every registered plugin with its owner identity, in the same order as {@link #getPlugins()}. */
    public static synchronized List<OwnedPlugin> getOwnedPlugins() {
        List<OwnedPlugin> view = ownedView;
        if (view != null) return view;
        view = ENTRIES.stream()
                .sorted(ORDER)
                .map(entry -> new OwnedPlugin(entry.identity(), entry.plugin()))
                .toList();
        ownedView = view;
        return view;
    }
}
