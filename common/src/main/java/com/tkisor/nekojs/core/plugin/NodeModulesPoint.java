package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.core.module.NodeModuleRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Built-in extension point {@code nekojs:node_modules}, self-contained in this file: the
 * contributor interface, the point definition and the accumulator all live here.
 *
 * <p>A plugin is collected by this point when it implements {@link Contributor}. The merge
 * policy is {@link MergePolicy#firstWin}: the first contribution for an id wins and a
 * duplicate logs a warning. The id constant {@link #ID} is observable: tests and diagnostics
 * read it from this file.
 */
public final class NodeModulesPoint {

    public static final String ID = "nekojs:node_modules";

    private static final Logger LOGGER = LoggerFactory.getLogger("nekojs.bootstrap");

    private static final MergePolicy POLICY = MergePolicy.firstWin();

    private NodeModulesPoint() {
    }

    /** Explicit declaration form: the plugin is collected by the {@code nekojs:node_modules}
     * extension point, the same as overriding the matching NekoJSPlugin hook. */
    public interface Contributor extends NekoJSPlugin {

        /**
         * Registers custom JS modules in CommonJS style; scripts load them with
         * {@code require('moduleId')}.
         *
         * <p>Completion declarations are separate: register {@code declare module 'moduleId' {...}}
         * through {@link com.tkisor.nekojs.core.plugin.TypeDocsRegister#registerManualDeclaration}
         * on the type_docs point, and the probe writes it to {@code @manual/index.d.ts}.
         */
        void registerNodeModules(NodeModuleRegister registry);
    }

    /**
     * Accumulator: the first contribution for an id wins and a duplicate logs a warning.
     * It seals after the snapshot, so nothing is collected once the point has finished.
     */
    static final class Bucket implements NodeModuleRegister, Sealable {
        private final Map<String, String> modules = new LinkedHashMap<>();
        private boolean sealed;

        @Override
        public void register(String moduleId, String source) {
            if (sealed) {
                throw new IllegalStateException("扩展点 " + ID + " 已 finish，累积器密封，不可再收集");
            }
            Objects.requireNonNull(moduleId, "moduleId");
            Objects.requireNonNull(source, "source");
            if (modules.containsKey(moduleId) && !POLICY.resolveDuplicate(ID, "plugin", moduleId, LOGGER)) {
                return;
            }
            modules.put(moduleId, source);
        }

        Map<String, String> snapshot() {
            sealed = true;
            return Collections.unmodifiableMap(new LinkedHashMap<>(modules));
        }

        @Override
        public void seal() {
            this.sealed = true;
        }
    }

    /** Extension point definition, registered by the {@link NekoBuiltinPointsPlugin} manifest
     * through the same provider path that third-party plugins use. */
    public static final NekoPluginExtensionPoint<NekoJSPlugin, Bucket, Map<String, String>> POINT =
            NekoPluginExtensionPoint.<NekoJSPlugin, Bucket, Map<String, String>>builder(ID, NekoJSPlugin.class)
                    .merge(POLICY)
                    .initializer(context -> new Bucket())
                    .collector(NekoJSPlugin::registerNodeModules)
                    .finish(Bucket::snapshot)
                    .build();
}
