package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.core.NekoJSBasePluginManager;
import com.tkisor.nekojs.api.data.AttachedData;
import com.tkisor.nekojs.script.ScriptType;

/**
 * 平台层 mixin 在首次访问 {@code host.data} 时调用本工具，遍历所有已加载插件触发对应的
 * {@link NekoJSPlugin#attachServerData} / {@link NekoJSPlugin#attachLevelData} / {@link NekoJSPlugin#attachPlayerData}。
 *
 * <p>单个插件 attach 异常被 try/catch 隔离（仿 {@code NekoPluginRuntime#runRecipeHooks}），不中断其他插件。
 * 集中在此处供三个平台模块的 mixin 复用，避免重复。
 */
public final class AttachedDataHooks {
    private AttachedDataHooks() {}

    public static void fireAttachServer(AttachedData<?> data) {
        for (NekoJSPlugin plugin : NekoJSBasePluginManager.getPlugins()) {
            try {
                plugin.attachServerData(data);
            } catch (Exception e) {
                ScriptType.SERVER.logger().error("attachServerData hook failed for " + plugin.getClass().getName(), e);
            }
        }
    }

    public static void fireAttachLevel(AttachedData<?> data) {
        for (NekoJSPlugin plugin : NekoJSBasePluginManager.getPlugins()) {
            try {
                plugin.attachLevelData(data);
            } catch (Exception e) {
                ScriptType.SERVER.logger().error("attachLevelData hook failed for " + plugin.getClass().getName(), e);
            }
        }
    }

    public static void fireAttachPlayer(AttachedData<?> data) {
        for (NekoJSPlugin plugin : NekoJSBasePluginManager.getPlugins()) {
            try {
                plugin.attachPlayerData(data);
            } catch (Exception e) {
                ScriptType.SERVER.logger().error("attachPlayerData hook failed for " + plugin.getClass().getName(), e);
            }
        }
    }
}
