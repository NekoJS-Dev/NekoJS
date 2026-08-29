package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.recipe.RecipeNamespaceEntry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 内置扩展点 {@code nekojs:recipe_namespaces}（ADR-0001 自包含 Point 文件）。
 * merge 策略 {@link MergePolicy#failFast}：命名空间冲突立即抛错。
 */
public final class RecipeNamespacesPoint {

    /** 扩展点 id（observable 契约）。 */
    public static final String ID = "nekojs:recipe_namespaces";

    private RecipeNamespacesPoint() {
    }

    /** 贡献面：实现本接口的插件被 {@code nekojs:recipe_namespaces} 扩展点收集。 */
    public interface Contributor extends NekoJSPlugin {

        /** 注册配方命名空间 Java handler——{@code event.recipes.<namespace>.<method>(...)} 的方法实现层。 */
        default void registerRecipeNamespaces(RecipeNamespaceRegister registry) {
        }
    }

    /** 累积器：命名空间冲突 fail-fast；snapshot 后密封。 */
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

    /** 扩展点定义（由 {@link NekoBuiltinPointsPlugin} 清单注册）。 */
    public static final NekoPluginExtensionPoint<Contributor, Bucket, Map<String, RecipeNamespaceEntry>> POINT =
            NekoPluginExtensionPoint.<Contributor, Bucket, Map<String, RecipeNamespaceEntry>>builder(ID, Contributor.class)
                    .merge(MergePolicy.failFast())
                    .initializer(context -> new Bucket())
                    .collector(Contributor::registerRecipeNamespaces)
                    .finish(Bucket::snapshot)
                    .build();
}
