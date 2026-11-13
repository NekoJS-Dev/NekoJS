package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;

/**
 * Lets third-party plugins define their own collection points, which run in the same batch as
 * the built-in ones.
 *
 * <p>A plugin that implements both {@link NekoJSPlugin} and this interface receives one
 * {@link #registerPluginExtensionPoints} callback during the extension point registration
 * phase of a bootstrap. There it registers any number of {@link NekoPluginExtensionPoint}
 * instances with the {@link NekoPluginExtensionRegistry}; each registration returns a
 * {@link NekoPluginExtensionHandle} that fetches the point's finisher product once the
 * bootstrap completes. After registration ends (the registry freezes), every point, built-in
 * or custom, collects from <b>all</b> plugins of this bootstrap one point at a time in
 * point-first execution order, including the plugin that defined it.
 *
 * <p><b>Lifetime:</b> the registration window is open only during the extension point
 * registration phase of {@link NekoPluginBootstrap}. The bootstrap registers the 14 built-in
 * points first ({@code nekojs:script_compilers} … {@code nekojs:probe_backends}), then calls
 * each provider in plugin list order, then freezes. The window closes with that bootstrap;
 * calling {@link NekoPluginExtensionRegistry#register} after the freeze throws
 * {@link IllegalStateException}.
 *
 * <p><b>Id conflicts:</b> an extension point id is unique within one bootstrap. A duplicate
 * between two custom ids, or between a custom id and a built-in {@code nekojs:*} id, throws
 * {@link IllegalArgumentException} at registration, and the fail-fast rule aborts the whole
 * bootstrap.
 *
 * <p><b>Point-first execution order:</b> after the freeze the bootstrap collects point by
 * point and plugin by plugin. For each point the initializer builds the accumulator, the
 * collectors run in plugin list order (the production entry point sorts plugins by
 * {@code @RegisterNekoJSPlugin} priority, highest first), the finisher produces an immutable
 * product, the product is published (bootstrap context, handle and result containers), and
 * the next point starts; points run in registration order, built-ins first, custom ones after.
 * A point therefore depends on another by using that earlier point's product in its
 * initializer or collector ({@link NekoPluginExtensionContext#result}): when the later point
 * collects any plugin, the earlier one has already finished and its product is available.
 *
 * <p><b>Relation to the built-in hooks:</b> the 14 built-in {@link NekoJSPlugin} hooks
 * ({@code registerScriptCompilers}, {@code registerBinding}, {@code registerEvents} and so on)
 * are not a separate channel; they are extension points registered by this same mechanism
 * (see {@code BuiltinPluginExtensionPoints}). Points registered through this interface have
 * the same standing and are collected in the same batch.
 */
public interface NekoPluginExtensionProvider extends NekoJSPlugin {

    /**
     * Registers the extension points this plugin defines with the bootstrap.
     *
     * <p>Called once, only during the extension point registration phase. Points registered
     * through {@link NekoPluginExtensionRegistry#register} in this phase are collected from
     * every plugin (including this one) after the freeze, in point-first execution order, and
     * the returned {@link NekoPluginExtensionHandle} fetches the product once the point
     * finishes. Registering after the window closes throws {@link IllegalStateException}; a
     * duplicate id throws {@link IllegalArgumentException}.
     *
     * @param registry the registrar supplied by the bootstrap (writable only during this
     *                 callback); the handle returned by {@code register} fetches the product
     *                 after the bootstrap completes
     */
    void registerPluginExtensionPoints(NekoPluginExtensionRegistry registry);
}
