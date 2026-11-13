package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.core.NekoJSBasePluginManager;
import com.tkisor.nekojs.api.data.AttachedData;
import com.tkisor.nekojs.api.ScriptType;

/**
 * Platform mixins call this helper on the first access to {@code host.data}. It walks every
 * loaded plugin and fires the matching
 * {@link NekoJSPlugin#attachServerData} / {@link NekoJSPlugin#attachLevelData} / {@link NekoJSPlugin#attachPlayerData}.
 *
 * <p>An exception from one plugin's attach is caught and isolated (like
 * {@code NekoPluginRuntime#runRecipeHooks}), so the remaining plugins still run. All three
 * platform modules reuse this single place instead of duplicating it.
 */
public final class AttachedDataHooks {
    private AttachedDataHooks() {}

    public static void fireAttachServer(AttachedData<?> data) {
        for (NekoJSPlugin plugin : NekoJSBasePluginManager.getPlugins()) {
            try {
                plugin.attachServerData(data);
            } catch (Exception e) {
                com.tkisor.nekojs.script.ScriptTypeEnv.logger(ScriptType.SERVER).error("attachServerData hook failed for " + plugin.getClass().getName(), e);
            }
        }
    }

    public static void fireAttachLevel(AttachedData<?> data) {
        for (NekoJSPlugin plugin : NekoJSBasePluginManager.getPlugins()) {
            try {
                plugin.attachLevelData(data);
            } catch (Exception e) {
                com.tkisor.nekojs.script.ScriptTypeEnv.logger(ScriptType.SERVER).error("attachLevelData hook failed for " + plugin.getClass().getName(), e);
            }
        }
    }

    public static void fireAttachPlayer(AttachedData<?> data) {
        for (NekoJSPlugin plugin : NekoJSBasePluginManager.getPlugins()) {
            try {
                plugin.attachPlayerData(data);
            } catch (Exception e) {
                com.tkisor.nekojs.script.ScriptTypeEnv.logger(ScriptType.SERVER).error("attachPlayerData hook failed for " + plugin.getClass().getName(), e);
            }
        }
    }
}
