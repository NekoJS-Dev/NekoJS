package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.recipe.RecipeLifecycleContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Built-in extension point {@code nekojs:recipe_lifecycle}. The merge policy is
 * {@link MergePolicy#append}: it collects hook lists, so no keys can conflict.
 */
public final class RecipeLifecyclePoint {

    /** Extension point id. */
    public static final String ID = "nekojs:recipe_lifecycle";

    private RecipeLifecyclePoint() {
    }

    /** Explicit declaration form: the plugin is collected by the {@code nekojs:recipe_lifecycle} extension point, the
     * same result as overriding the matching NekoJSPlugin hook directly. */
    public interface Contributor extends NekoJSPlugin {

        /**
         * Registers recipe lifecycle hooks. The default implementation registers
         * {@code beforeRecipeLoading} and {@code afterRecipes}, the convenience hooks on NekoJSPlugin;
         * a plugin overrides those two methods when it needs different behavior.
         */
        default void registerRecipeLifecycleHooks(RecipeLifecycleRegister registry) {
            registry.beforeRecipeLoading(this::beforeRecipeLoading);
            registry.afterRecipes(this::afterRecipes);
        }
    }

    record RecipeLifecycleHooks(
            List<Consumer<RecipeLifecycleContext>> beforeRecipeLoading,
            List<Consumer<RecipeLifecycleContext>> afterRecipes) {
        RecipeLifecycleHooks {
            beforeRecipeLoading = List.copyOf(beforeRecipeLoading);
            afterRecipes = List.copyOf(afterRecipes);
        }
    }

    /** Accumulator holding the two hook lists. It is sealed after snapshot. */
    static final class Bucket implements RecipeLifecycleRegister, Sealable {
        private final List<Consumer<RecipeLifecycleContext>> beforeRecipeLoadingHooks = new ArrayList<>();
        private final List<Consumer<RecipeLifecycleContext>> afterRecipesHooks = new ArrayList<>();
        private boolean sealed;

        @Override
        public void beforeRecipeLoading(Consumer<RecipeLifecycleContext> hook) {
            checkSealed();
            beforeRecipeLoadingHooks.add(Objects.requireNonNull(hook, "hook"));
        }

        @Override
        public void afterRecipes(Consumer<RecipeLifecycleContext> hook) {
            checkSealed();
            afterRecipesHooks.add(Objects.requireNonNull(hook, "hook"));
        }

        private void checkSealed() {
            if (sealed) {
                throw new IllegalStateException("扩展点 " + ID + " 已 finish，累积器密封，不可再收集");
            }
        }

        RecipeLifecycleHooks snapshot() {
            sealed = true;
            return new RecipeLifecycleHooks(beforeRecipeLoadingHooks, afterRecipesHooks);
        }

        @Override
        public void seal() {
            this.sealed = true;
        }
    }

    /** The extension point definition, registered from the {@link NekoBuiltinPointsPlugin} list. */
    public static final NekoPluginExtensionPoint<NekoJSPlugin, Bucket, RecipeLifecycleHooks> POINT =
            NekoPluginExtensionPoint.<NekoJSPlugin, Bucket, RecipeLifecycleHooks>builder(ID, NekoJSPlugin.class)
                    .merge(MergePolicy.append())
                    .initializer(context -> new Bucket())
                    .collector(NekoJSPlugin::registerRecipeLifecycleHooks)
                    .finish(Bucket::snapshot)
                    .build();
}
