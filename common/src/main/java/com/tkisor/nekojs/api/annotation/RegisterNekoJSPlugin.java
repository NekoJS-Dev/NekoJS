package com.tkisor.nekojs.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a NekoJS plugin for auto-discovery.
 * The annotated class must implement {@link com.tkisor.nekojs.api.NekoJSPlugin}.
 *
 * <p>Plugins are discovered via {@code NeoForgePluginLoader.loadAnnotatedPlugins()}
 * during mod initialization and registered with the plugin bootstrap system.
 * Plugins are loaded in descending priority order (highest priority first).
 *
 * <p><b>Ordering contract:</b> plugins sharing the same priority are registered in
 * discovery/scan order, which is not stable across platforms — never rely on
 * relative order between same-priority plugins. {@code NekoJSPlugin.CORE_PRIORITY}
 * is reserved for the platform core infrastructure plugin; third-party plugins
 * should stay at the default (or lower it) unless they must register before
 * other third-party plugins.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RegisterNekoJSPlugin {
    /** Only load this plugin on the client process (skipped on a dedicated server). */
    boolean clientOnly() default false;

    /** Only load this plugin when all of the listed mods are present (AND semantics). */
    String[] requiredMods() default {};

    /** Load priority — higher values load first. Default 1000. Same-priority order is undefined. */
    int priority() default 1000;
}
