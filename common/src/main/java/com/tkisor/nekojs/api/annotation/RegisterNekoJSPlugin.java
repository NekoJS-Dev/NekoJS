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
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RegisterNekoJSPlugin {
}
