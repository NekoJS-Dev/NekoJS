package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;

/**
 * Built-in extension point {@code nekojs:script_properties}.
 *
 * <p>This one is a closure: the accumulator is the {@link ScriptPropertyRegistry} passed in by
 * bootstrap, and its value differs on every round. There is therefore no static constant.
 * {@link #point(ScriptPropertyRegistry)} builds the point at registration time instead.
 */
public final class ScriptPropertiesPoint {

    /** Extension point id. */
    public static final String ID = "nekojs:script_properties";

    private ScriptPropertiesPoint() {
    }

    /** Explicit declaration form: the plugin is collected by the {@code nekojs:script_properties} extension point, the
     * same result as overriding the matching NekoJSPlugin hook directly. */
    public interface Contributor extends NekoJSPlugin {

        /** Registers a script property, one of the file header properties such as {@code AFTER},
         * {@code MODLOADED}, {@code DISABLE} and {@code PRIORITY}. */
        default void registerScriptProperty(ScriptPropertyRegistry registry) {
        }
    }

    /** The extension point definition, built when the {@link NekoBuiltinPointsPlugin} list is registered. */
    public static NekoPluginExtensionPoint<NekoJSPlugin, ScriptPropertyRegistry, ScriptPropertyRegistry> point(
            ScriptPropertyRegistry scriptProperties) {
        return NekoPluginExtensionPoint.<NekoJSPlugin, ScriptPropertyRegistry, ScriptPropertyRegistry>builder(ID, NekoJSPlugin.class)
                .merge(MergePolicy.append())
                .initializer(context -> scriptProperties)
                .collector(NekoJSPlugin::registerScriptProperty)
                .finish(registry -> {
                    if (registry instanceof ScriptPropertyRegistry.Impl impl) {
                        impl.freeze();
                    }
                    return registry;
                })
                .build();
    }
}
