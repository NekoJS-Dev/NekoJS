package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.NekoJSBasePluginManager;
import com.tkisor.nekojs.wrapper.DataGeneratorJS;
import com.tkisor.nekojs.wrapper.LangGeneratorJS;

/**
 * 平台层在资源 reload 时调用本工具，遍历所有已加载插件触发对应的
 * {@link NekoJSPlugin#generateData} / {@link NekoJSPlugin#generateAssets} / {@link NekoJSPlugin#generateLang}。
 *
 * <p>插件 hook 先于脚本事件触发，与脚本共享同一 generator 实例（KubeJS 对齐）。
 * 单个插件异常被 try/catch 隔离，不中断其他插件。
 */
public final class PluginGenerationHooks {
    private PluginGenerationHooks() {}

    public static void fireGenerateData(DataGeneratorJS generator) {
        for (NekoJSPlugin plugin : NekoJSBasePluginManager.getPlugins()) {
            try {
                plugin.generateData(generator);
            } catch (Exception e) {
                ScriptType.SERVER.logger().error("generateData hook failed for " + plugin.getClass().getName(), e);
            }
        }
    }

    public static void fireGenerateAssets(DataGeneratorJS generator) {
        for (NekoJSPlugin plugin : NekoJSBasePluginManager.getPlugins()) {
            try {
                plugin.generateAssets(generator);
            } catch (Exception e) {
                ScriptType.CLIENT.logger().error("generateAssets hook failed for " + plugin.getClass().getName(), e);
            }
        }
    }

    public static void fireGenerateLang(LangGeneratorJS generator) {
        for (NekoJSPlugin plugin : NekoJSBasePluginManager.getPlugins()) {
            try {
                plugin.generateLang(generator);
            } catch (Exception e) {
                ScriptType.CLIENT.logger().error("generateLang hook failed for " + plugin.getClass().getName(), e);
            }
        }
    }
}
