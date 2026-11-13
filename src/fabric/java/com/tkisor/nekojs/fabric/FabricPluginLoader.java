package com.tkisor.nekojs.fabric;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.core.NekoJSBasePluginManager;
import com.tkisor.nekojs.core.plugin.NekoCommonBuiltinPlugin;
import com.tkisor.nekojs.core.compiler.python.PythonTranspilerPlugin;
import com.tkisor.nekojs.probe.NekoProbeBuiltinPlugin;
import com.tkisor.nekojs.probe.MinecraftTypeDocsPlugin;
import com.tkisor.nekojs.util.selector.EntitySelectorsPlugin;
import com.tkisor.nekojs.wrapper.registry.gen.NekoRegistryPointsPlugin;
import net.fabricmc.loader.api.FabricLoader;

import java.util.List;

/**
 * Finds Fabric plugins. This is the equivalent of the annotation scan in
 * {@code NeoForgePluginLoader} on the NeoForge side:
 *
 * <ol>
 *   <li><b>Built-in list</b>: {@code @RegisterNekoJSPlugin} plugins in common and the shared
 *       tree that have <b>no platform guard</b> are listed explicitly. NeoForge relies on the
 *       FML annotation scan over the whole classpath, and the fabric loader has no such
 *       mechanism, so a new unguarded plugin must be added to this list. This list is the only
 *       registration friction on fabric;</li>
 *   <li><b>{@code nekojs} entrypoint</b>: a third-party fabric integration is discovered once
 *       it declares the entrypoint in fabric.mod.json, which is as free as the annotation
 *       scan is for third parties on NeoForge.</li>
 * </ol>
 */
public final class FabricPluginLoader {
    private FabricPluginLoader() {}

    /**
     * The built-in plugins: common global bindings/compiler/probe, the general registry and the
     * entity selectors. ({@code NekoBuiltinPointsPlugin} is absent because bootstrap constructs
     * it internally with arguments; it is not registered by scanning.)
     */
    private static final List<Class<?>> BUILTIN_PLUGINS = List.of(
            NekoCommonBuiltinPlugin.class,
            NekoProbeBuiltinPlugin.class,
            MinecraftTypeDocsPlugin.class,
            PythonTranspilerPlugin.class,
            NekoRegistryPointsPlugin.class,
            EntitySelectorsPlugin.class,
            FabricCorePlugin.class);

    public static void loadPlugins() {
        for (Class<?> plugin : BUILTIN_PLUGINS) {
            NekoJSBasePluginManager.registerClass("nekojs", plugin);
        }
        for (var container : FabricLoader.getInstance().getEntrypointContainers("nekojs", NekoJSPlugin.class)) {
            NekoJSBasePluginManager.registerClass(container.getProvider().getMetadata().getId(),
                    container.getEntrypoint().getClass());
        }
    }
}
