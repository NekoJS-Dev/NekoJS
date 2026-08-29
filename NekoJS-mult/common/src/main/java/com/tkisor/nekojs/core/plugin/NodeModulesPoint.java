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
 * 内置扩展点 {@code nekojs:node_modules}（ADR-0001 自包含 Point 文件）：
 * 贡献面（Contributor）+ 定义（POINT）+ 累积器（Bucket）同处一文件。
 *
 * <p>插件实现 {@link Contributor} 即被本扩展点收集；merge 策略为
 * {@link MergePolicy#firstWin}（同 id 首胜 + warn）。id 常量 {@link #ID}
 * 是 observable 契约（测试与诊断依赖），从本文件读取。
 */
public final class NodeModulesPoint {

    public static final String ID = "nekojs:node_modules";

    private static final Logger LOGGER = LoggerFactory.getLogger("nekojs.bootstrap");

    private static final MergePolicy POLICY = MergePolicy.firstWin();

    private NodeModulesPoint() {
    }

    /** 贡献面：实现本接口的插件被 {@code nekojs:node_modules} 扩展点收集。 */
    public interface Contributor extends NekoJSPlugin {

        /**
         * 注册插件自定义 JS 模块（CommonJS 风格），脚本可通过 {@code require('moduleId')} 加载。
         *
         * <p>补全声明需另行通过 type_docs 扩展点的
         * {@link com.tkisor.nekojs.core.plugin.TypeDocsRegister#registerManualDeclaration} 注册
         * {@code declare module 'moduleId' {...}}（probe 输出到 {@code @manual/index.d.ts}）。
         */
        void registerNodeModules(NodeModuleRegister registry);
    }

    /**
     * 累积器：同 id 首胜并告警；snapshot 后密封（finish 后不再收集，ADR-0001）。
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

    /** 扩展点定义（由 {@link NekoBuiltinPointsPlugin} 清单注册，与第三方同一条 provider 路径）。 */
    public static final NekoPluginExtensionPoint<Contributor, Bucket, Map<String, String>> POINT =
            NekoPluginExtensionPoint.<Contributor, Bucket, Map<String, String>>builder(ID, Contributor.class)
                    .merge(POLICY)
                    .initializer(context -> new Bucket())
                    .collector(Contributor::registerNodeModules)
                    .finish(Bucket::snapshot)
                    .build();
}
