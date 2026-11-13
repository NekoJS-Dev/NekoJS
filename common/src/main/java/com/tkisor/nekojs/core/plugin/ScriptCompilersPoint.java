package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;

/**
 * Built-in extension point {@code nekojs:script_compilers}. The accumulator is the
 * {@link ScriptCompilerRegistry} itself, and the merge policy is append.
 */
public final class ScriptCompilersPoint {

    /** Extension point id. */
    public static final String ID = "nekojs:script_compilers";

    private ScriptCompilersPoint() {
    }

    /** Explicit declaration form: the plugin is collected by the {@code nekojs:script_compilers} extension point, the
     * same result as overriding the matching NekoJSPlugin hook directly. */
    public interface Contributor extends NekoJSPlugin {

        /** Registers a script compiler for a language. The compiler runs when scripts load and when they hot reload. */
        default void registerScriptCompilers(ScriptCompilerRegistry registry) {
        }
    }

    /** The extension point definition, registered from the {@link NekoBuiltinPointsPlugin} list. */
    public static final NekoPluginExtensionPoint<NekoJSPlugin, ScriptCompilerRegistry, ScriptCompilerRegistry> POINT =
            NekoPluginExtensionPoint.<NekoJSPlugin, ScriptCompilerRegistry, ScriptCompilerRegistry>builder(ID, NekoJSPlugin.class)
                    .merge(MergePolicy.append())
                    .initializer(context -> ScriptCompilerRegistry.createRuntimeRegistry())
                    .collector(NekoJSPlugin::registerScriptCompilers)
                    .finish(registry -> {
                        registry.freeze();
                        return registry;
                    })
                    .build();
}
