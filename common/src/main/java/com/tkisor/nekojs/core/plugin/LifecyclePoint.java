package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.ScriptType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 内置扩展点 {@code nekojs:lifecycle}（ADR-0001 自包含 Point 文件）。
 * merge 策略 {@link MergePolicy#append}（五个生命周期钩子列表，无键冲突）。
 */
public final class LifecyclePoint {

    /** 扩展点 id（observable 契约）。 */
    public static final String ID = "nekojs:lifecycle";

    private LifecyclePoint() {
    }

    /** 贡献面：实现本接口的插件被 {@code nekojs:lifecycle} 扩展点收集。 */
    public interface Contributor extends NekoJSPlugin {

        /**
         * 注册插件生命周期钩子。默认实现注册 {@code init} / {@code initStartup} / {@code afterInit}
         * 以及 {@code beforeScriptsLoaded} / {@code afterScriptsLoaded}（NekoJSPlugin 便捷钩子），
         * 插件按需覆盖对应便捷方法即可。
         */
        default void registerLifecycleHooks(PluginLifecycleRegister registry) {
            registry.onInit(this::init);
            registry.onInitStartup(this::initStartup);
            registry.onAfterInit(this::afterInit);
            registry.onBeforeScriptsLoaded(this::beforeScriptsLoaded);
            registry.onAfterScriptsLoaded(this::afterScriptsLoaded);
        }
    }

    record LifecycleHooks(
            List<Runnable> init,
            List<Runnable> initStartup,
            List<Runnable> afterInit,
            List<Consumer<ScriptType>> beforeScriptsLoaded,
            List<Consumer<ScriptType>> afterScriptsLoaded) {
        LifecycleHooks {
            init = List.copyOf(init);
            initStartup = List.copyOf(initStartup);
            afterInit = List.copyOf(afterInit);
            beforeScriptsLoaded = List.copyOf(beforeScriptsLoaded);
            afterScriptsLoaded = List.copyOf(afterScriptsLoaded);
        }
    }

    /** 累积器：五个钩子列表；snapshot 后密封。 */
    static final class Bucket implements PluginLifecycleRegister, Sealable {
        private final List<Runnable> initHooks = new ArrayList<>();
        private final List<Runnable> initStartupHooks = new ArrayList<>();
        private final List<Runnable> afterInitHooks = new ArrayList<>();
        private final List<Consumer<ScriptType>> beforeScriptsLoadedHooks = new ArrayList<>();
        private final List<Consumer<ScriptType>> afterScriptsLoadedHooks = new ArrayList<>();
        private boolean sealed;

        private void checkSealed() {
            if (sealed) {
                throw new IllegalStateException("扩展点 " + ID + " 已 finish，累积器密封，不可再收集");
            }
        }

        @Override
        public void onInit(Runnable hook) {
            checkSealed();
            initHooks.add(Objects.requireNonNull(hook, "hook"));
        }

        @Override
        public void onInitStartup(Runnable hook) {
            checkSealed();
            initStartupHooks.add(Objects.requireNonNull(hook, "hook"));
        }

        @Override
        public void onAfterInit(Runnable hook) {
            checkSealed();
            afterInitHooks.add(Objects.requireNonNull(hook, "hook"));
        }

        @Override
        public void onBeforeScriptsLoaded(Consumer<ScriptType> hook) {
            checkSealed();
            beforeScriptsLoadedHooks.add(Objects.requireNonNull(hook, "hook"));
        }

        @Override
        public void onAfterScriptsLoaded(Consumer<ScriptType> hook) {
            checkSealed();
            afterScriptsLoadedHooks.add(Objects.requireNonNull(hook, "hook"));
        }

        LifecycleHooks snapshot() {
            sealed = true;
            return new LifecycleHooks(initHooks, initStartupHooks, afterInitHooks,
                    beforeScriptsLoadedHooks, afterScriptsLoadedHooks);
        }

        @Override
        public void seal() {
            this.sealed = true;
        }
    }

    /** 扩展点定义（由 {@link NekoBuiltinPointsPlugin} 清单注册）。 */
    public static final NekoPluginExtensionPoint<Contributor, Bucket, LifecycleHooks> POINT =
            NekoPluginExtensionPoint.<Contributor, Bucket, LifecycleHooks>builder(ID, Contributor.class)
                    .merge(MergePolicy.append())
                    .initializer(context -> new Bucket())
                    .collector(Contributor::registerLifecycleHooks)
                    .finish(Bucket::snapshot)
                    .build();
}
