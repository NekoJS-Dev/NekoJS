package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.NekoJSBasePluginManager;
import com.tkisor.nekojs.script.ScriptTypeEnv;
import com.tkisor.nekojs.wrapper.DataGeneratorJS;
import com.tkisor.nekojs.wrapper.LangGeneratorJS;

import java.util.function.BiConsumer;

/**
 * 平台层在资源 reload 时对全部插件触发 generateData / generateAssets / generateLang
 * （直调型钩子：default 空实现，覆写即生效，无需任何注册/实现声明）。
 *
 * <p>插件 hook 先于脚本事件触发，与脚本共享同一 generator 实例（KubeJS 对齐）。
 * 单个插件异常被 try/catch 隔离，不中断其他插件。
 */
public final class PluginGenerationHooks {
    private PluginGenerationHooks() {}

    public static void fireGenerateData(DataGeneratorJS generator) {
        fire("generateData", ScriptType.SERVER, generator, NekoJSPlugin::generateData);
    }

    public static void fireGenerateAssets(DataGeneratorJS generator) {
        fire("generateAssets", ScriptType.CLIENT, generator, NekoJSPlugin::generateAssets);
    }

    public static void fireGenerateLang(LangGeneratorJS generator) {
        fire("generateLang", ScriptType.CLIENT, generator, NekoJSPlugin::generateLang);
    }

    /** 全员触发 + 异常隔离的公共形状：钩子名进日志、env 定 logger、generator 类型参数化。 */
    private static <G> void fire(String hook, ScriptType env, G generator,
            BiConsumer<NekoJSPlugin, G> call) {
        for (NekoJSPlugin plugin : NekoJSBasePluginManager.getPlugins()) {
            try {
                call.accept(plugin, generator);
            } catch (Exception e) {
                ScriptTypeEnv.logger(env).error(hook + " hook failed for " + plugin.getClass().getName(), e);
            }
        }
    }
}
