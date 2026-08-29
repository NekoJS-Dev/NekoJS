package com.tkisor.nekojs.fabric;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.core.NekoJSBasePluginManager;
import com.tkisor.nekojs.core.plugin.NekoCommonBuiltinPlugin;
import com.tkisor.nekojs.core.compiler.python.PythonTranspilerPlugin;
import com.tkisor.nekojs.probe.NekoProbeBuiltinPlugin;
import com.tkisor.nekojs.util.selector.EntitySelectorsPlugin;
import com.tkisor.nekojs.wrapper.registry.gen.NekoRegistryPointsPlugin;
import net.fabricmc.loader.api.FabricLoader;

import java.util.List;

/**
 * Fabric 侧插件发现（与 NeoForge 侧 {@code NeoForgePluginLoader} 的注解扫描等价）：
 *
 * <ol>
 *   <li><b>内置清单</b>：common 与共享树中<b>无平台守卫</b>的 {@code @RegisterNekoJSPlugin}
 *       插件显式列出（NeoForge 靠 FML 注解扫描覆盖全类路径，fabric loader 无此机制）；
 *       新增无守卫插件时须同步本清单——这也是 fabric 端唯一的注册点摩擦；</li>
 *   <li><b>{@code nekojs} entrypoint</b>：第三方 fabric 集成在 fabric.mod.json 声明
 *       entrypoint 即被发现（与 NeoForge 端注解扫描对第三方等价自由）。</li>
 * </ol>
 */
public final class FabricPluginLoader {
    private FabricPluginLoader() {}

    /**
     * 内置插件：common 全局绑定/编译器/探测 + 通用注册表 + 实体选择器。
     * （NekoBuiltinPointsPlugin 不在列——它由 bootstrap 内部带参构造，不经扫描注册。）
     */
    private static final List<Class<?>> BUILTIN_PLUGINS = List.of(
            NekoCommonBuiltinPlugin.class,
            NekoProbeBuiltinPlugin.class,
            PythonTranspilerPlugin.class,
            NekoRegistryPointsPlugin.class,
            EntitySelectorsPlugin.class,
            FabricCorePlugin.class);

    public static void loadPlugins() {
        for (Class<?> plugin : BUILTIN_PLUGINS) {
            NekoJSBasePluginManager.registerClass(plugin);
        }
        for (var container : FabricLoader.getInstance().getEntrypointContainers("nekojs", NekoJSPlugin.class)) {
            NekoJSBasePluginManager.registerClass(container.getEntrypoint().getClass());
        }
    }
}
