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
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RegisterNekoJSPlugin {
    /** Only load this plugin on the client process (skipped on a dedicated server). */
    boolean clientOnly() default false;

    /** Only load this plugin when all of the listed mods are present (AND semantics). */
    String[] requiredMods() default {};

    /** Load priority — higher values load first. Default 1000. */
    int priority() default 1000;
}
