package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Built-in extension point {@code nekojs:recipe_schemas}. The merge policy is
 * {@link MergePolicy#firstWin}: the first entry for a {@code (namespace, type)} pair wins and
 * the later entry logs a warning.
 */
public final class RecipeSchemasPoint {

    /** Extension point id. */
    public static final String ID = "nekojs:recipe_schemas";

    private static final Logger LOGGER = LoggerFactory.getLogger("nekojs.bootstrap");
    private static final MergePolicy POLICY = MergePolicy.firstWin();

    private RecipeSchemasPoint() {
    }

    /** Explicit declaration form: the plugin is collected by the {@code nekojs:recipe_schemas} extension point, the
     * same result as overriding the matching NekoJSPlugin hook directly. */
    public interface Contributor extends NekoJSPlugin {

        /** Registers a recipe schema override. The first entry for a {@code (namespace, type)} pair wins. */
        default void registerRecipeSchemas(RecipeSchemaRegister registry) {
        }
    }

    /** Accumulator: the first entry for a {@code (namespace, type)} pair wins and a warning is logged. It
     * is sealed after snapshot. */
    static final class Bucket implements RecipeSchemaRegister, Sealable {
        private final Map<String, Map<String, RecipeTypeDefinition>> overrides = new LinkedHashMap<>();
        private boolean sealed;

        @Override
        public void register(String namespace, String type, RecipeTypeDefinition schema) {
            if (sealed) {
                throw new IllegalStateException("扩展点 " + ID + " 已 finish，累积器密封，不可再收集");
            }
            Objects.requireNonNull(namespace, "namespace");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(schema, "schema");
            String key = namespace + ":" + type;
            Map<String, RecipeTypeDefinition> inner =
                    overrides.computeIfAbsent(namespace, ignored -> new LinkedHashMap<>());
            if (inner.containsKey(type) && !POLICY.resolveDuplicate(ID, "plugin", key, LOGGER)) {
                return;
            }
            inner.put(type, schema);
        }

        Map<String, Map<String, RecipeTypeDefinition>> snapshot() {
            sealed = true;
            Map<String, Map<String, RecipeTypeDefinition>> copy = new LinkedHashMap<>();
            for (var entry : overrides.entrySet()) {
                copy.put(entry.getKey(), Map.copyOf(entry.getValue()));
            }
            return Collections.unmodifiableMap(copy);
        }

        @Override
        public void seal() {
            this.sealed = true;
        }
    }

    /** The extension point definition, registered from the {@link NekoBuiltinPointsPlugin} list. */
    public static final NekoPluginExtensionPoint<NekoJSPlugin, Bucket, Map<String, Map<String, RecipeTypeDefinition>>> POINT =
            NekoPluginExtensionPoint.<NekoJSPlugin, Bucket, Map<String, Map<String, RecipeTypeDefinition>>>builder(ID, NekoJSPlugin.class)
                    .merge(POLICY)
                    .initializer(context -> new Bucket())
                    .collector(NekoJSPlugin::registerRecipeSchemas)
                    .finish(Bucket::snapshot)
                    .build();
}
