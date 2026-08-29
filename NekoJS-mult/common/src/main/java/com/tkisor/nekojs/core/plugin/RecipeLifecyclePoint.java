package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.recipe.RecipeLifecycleContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 内置扩展点 {@code nekojs:recipe_lifecycle}（ADR-0001 自包含 Point 文件）。
 * merge 策略 {@link MergePolicy#append}（钩子列表收集，无键冲突）。
 */
public final class RecipeLifecyclePoint {

    /** 扩展点 id（observable 契约）。 */
    public static final String ID = "nekojs:recipe_lifecycle";

    private RecipeLifecyclePoint() {
    }

    /** 显式声明形态（与直接覆写 NekoJSPlugin 对应钩子等价收集）：插件被 {@code nekojs:recipe_lifecycle} 扩展点收集。 */
    public interface Contributor extends NekoJSPlugin {

        /**
         * 注册配方生命周期钩子。默认实现注册 {@code beforeRecipeLoading} 与 {@code afterRecipes}
         * （NekoJSPlugin 便捷钩子），插件按需覆盖这两个便捷方法即可。
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

    /** 累积器：两个钩子列表；snapshot 后密封。 */
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

    /** 扩展点定义（由 {@link NekoBuiltinPointsPlugin} 清单注册）。 */
    public static final NekoPluginExtensionPoint<NekoJSPlugin, Bucket, RecipeLifecycleHooks> POINT =
            NekoPluginExtensionPoint.<NekoJSPlugin, Bucket, RecipeLifecycleHooks>builder(ID, NekoJSPlugin.class)
                    .merge(MergePolicy.append())
                    .initializer(context -> new Bucket())
                    .collector(NekoJSPlugin::registerRecipeLifecycleHooks)
                    .finish(Bucket::snapshot)
                    .build();
}
