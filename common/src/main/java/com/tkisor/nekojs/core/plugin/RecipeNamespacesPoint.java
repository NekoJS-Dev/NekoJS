package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.recipe.RecipeNamespaceEntry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Built-in extension point {@code nekojs:recipe_namespaces}. The merge policy is
 * {@link MergePolicy#failFast}: a namespace conflict throws immediately.
 */
public final class RecipeNamespacesPoint {

    /** Extension point id. */
    public static final String ID = "nekojs:recipe_namespaces";

    private RecipeNamespacesPoint() {
    }

    /** Explicit declaration form: the plugin is collected by the {@code nekojs:recipe_namespaces} extension point, the
     * same result as overriding the matching NekoJSPlugin hook directly. */
    public interface Contributor extends NekoJSPlugin {

        /** Registers the Java handler for a recipe namespace. It implements the methods behind
         * {@code event.recipes.<namespace>.<method>(...)}. */
        default void registerRecipeNamespaces(RecipeNamespaceRegister registry) {
        }
    }

    /** Accumulator: a namespace conflict fails fast; it is sealed after snapshot. */
    static final class Bucket implements RecipeNamespaceRegister, Sealable {
        private final Map<String, RecipeNamespaceEntry> namespaces = new LinkedHashMap<>();
        private boolean sealed;

        @Override
        public void register(RecipeNamespaceEntry entry) {
            if (sealed) {
                throw new IllegalStateException("扩展点 " + ID + " 已 finish，累积器密封，不可再收集");
            }
            Objects.requireNonNull(entry, "entry");
            if (namespaces.containsKey(entry.namespace())
                    && !MergePolicy.failFast().resolveDuplicate(ID, "plugin", entry.namespace(),
                    org.slf4j.LoggerFactory.getLogger("nekojs.bootstrap"))) {
                return;
            }
            namespaces.put(entry.namespace(), entry);
        }

        Map<String, RecipeNamespaceEntry> snapshot() {
            sealed = true;
            return Collections.unmodifiableMap(new LinkedHashMap<>(namespaces));
        }

        @Override
        public void seal() {
            this.sealed = true;
        }
    }

    /** The extension point definition, registered from the {@link NekoBuiltinPointsPlugin} list. */
    public static final NekoPluginExtensionPoint<NekoJSPlugin, Bucket, Map<String, RecipeNamespaceEntry>> POINT =
            NekoPluginExtensionPoint.<NekoJSPlugin, Bucket, Map<String, RecipeNamespaceEntry>>builder(ID, NekoJSPlugin.class)
                    .merge(MergePolicy.failFast())
                    .initializer(context -> new Bucket())
                    .collector(NekoJSPlugin::registerRecipeNamespaces)
                    .finish(Bucket::snapshot)
                    .build();
}
