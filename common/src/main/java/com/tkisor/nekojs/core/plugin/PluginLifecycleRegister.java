package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.ScriptType;

import java.util.function.Consumer;

/**
 * Registrar for plugin lifecycle hooks.
 *
 * <p>A {@link com.tkisor.nekojs.api.NekoJSPlugin} receives it in {@code registerLifecycleHooks},
 * and plugins usually never call this interface directly: overriding the convenience methods
 * on {@code NekoJSPlugin} ({@code init} / {@code initStartup} / {@code afterInit} /
 * {@code beforeScriptsLoaded} / {@code afterScriptsLoaded}) is enough, because the default
 * implementation registers the method references for them.
 *
 * <p>Use the registrar directly only to register several callbacks, or to control when the
 * registration happens.
 */
public interface PluginLifecycleRegister {

    /** Fires earliest: after runtime bootstrap completes and before startup scripts load. */
    void onInit(Runnable hook);

    /** Fires after the startup scripts have loaded. */
    void onInitStartup(Runnable hook);

    /** Fires after all mods finish initialization (FMLLoadCompleteEvent). */
    void onAfterInit(Runnable hook);

    /** Fires before each load of one script type; this covers the first load and full reloads, not
     * single-file hot reload. */
    void onBeforeScriptsLoaded(Consumer<ScriptType> hook);

    /** Runs after the scripts of one script type load, on first load and full reload, but not on single-file hot reload. */
    void onAfterScriptsLoaded(Consumer<ScriptType> hook);
}
