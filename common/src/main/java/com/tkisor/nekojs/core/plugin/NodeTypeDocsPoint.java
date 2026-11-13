package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;

/**
 * Built-in extension point {@code nekojs:node_type_docs}, the twin of
 * {@code nekojs:type_docs} ({@link TypeDocsPoint}): both share the accumulator
 * {@link TypeDocsPoint.Bucket} and the product {@link TypeDocsPoint.TypeDocsSnapshot}, and
 * they differ only in the set of plugins they collect from (the Contributor interface). The
 * merge policy is append, the same as type_docs.
 */
public final class NodeTypeDocsPoint {

    /** Extension point id; tests and diagnostics depend on this value. */
    public static final String ID = "nekojs:node_type_docs";

    private NodeTypeDocsPoint() {
    }

    /** Explicit declaration form: the plugin is collected by the {@code nekojs:node_type_docs}
     * extension point, the same as overriding the matching NekoJSPlugin hook. */
    public interface Contributor extends NekoJSPlugin {

        /**
         * Registers completion declarations for node modules
         * ({@code declare module 'node:xxx' {...}}). It pairs with the {@code node_modules}
         * point: that one supplies the module implementation, this method supplies the type
         * declarations.
         */
        default void registerNodeTypeDocs(TypeDocsRegister registry) {
        }
    }

    /** Extension point definition, registered by the {@link NekoBuiltinPointsPlugin} manifest. */
    public static final NekoPluginExtensionPoint<NekoJSPlugin, TypeDocsPoint.Bucket, TypeDocsPoint.TypeDocsSnapshot> POINT =
            NekoPluginExtensionPoint.<NekoJSPlugin, TypeDocsPoint.Bucket, TypeDocsPoint.TypeDocsSnapshot>builder(ID, NekoJSPlugin.class)
                    .merge(MergePolicy.append())
                    .initializer(context -> new TypeDocsPoint.Bucket())
                    .collector(NekoJSPlugin::registerNodeTypeDocs)
                    .finish(TypeDocsPoint.Bucket::snapshot)
                    .build();
}
