package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.ScriptType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Built-in extension point {@code nekojs:lifecycle}, self-contained in this file. Merge policy
 * is {@link MergePolicy#append}: five lifecycle hook lists, so no key ever conflicts.
 */
public final class LifecyclePoint {

    /** Extension point id; tests and diagnostics depend on this value. */
    public static final String ID = "nekojs:lifecycle";

    private LifecyclePoint() {
    }

    /** Explicit declaration form: the plugin is collected by the {@code nekojs:lifecycle} extension
     * point, the same as overriding the matching NekoJSPlugin hook. */
    public interface Contributor extends NekoJSPlugin {

        /**
         * Registers the plugin's lifecycle hooks. The default implementation registers
         * {@code init} / {@code initStartup} / {@code afterInit} and
         * {@code beforeScriptsLoaded} / {@code afterScriptsLoaded} (the NekoJSPlugin convenience
         * hooks), so a plugin only overrides the convenience method it needs.
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

    /** Accumulator: the five hook lists. It seals after the snapshot. */
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

    /** Extension point definition, registered by the {@link NekoBuiltinPointsPlugin} manifest. */
    public static final NekoPluginExtensionPoint<NekoJSPlugin, Bucket, LifecycleHooks> POINT =
            NekoPluginExtensionPoint.<NekoJSPlugin, Bucket, LifecycleHooks>builder(ID, NekoJSPlugin.class)
                    .merge(MergePolicy.append())
                    .initializer(context -> new Bucket())
                    .collector(NekoJSPlugin::registerLifecycleHooks)
                    .finish(Bucket::snapshot)
                    .build();
}
