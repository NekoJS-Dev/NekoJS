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
 * 内置扩展点 {@code nekojs:recipe_schemas}（ADR-0001 自包含 Point 文件）。
 * merge 策略 {@link MergePolicy#firstWin}：同 {@code (namespace, type)} 首胜 + warn。
 */
public final class RecipeSchemasPoint {

    /** 扩展点 id（observable 契约）。 */
    public static final String ID = "nekojs:recipe_schemas";

    private static final Logger LOGGER = LoggerFactory.getLogger("nekojs.bootstrap");
    private static final MergePolicy POLICY = MergePolicy.firstWin();

    private RecipeSchemasPoint() {
    }

    /** 显式声明形态（与直接覆写 NekoJSPlugin 对应钩子等价收集）：插件被 {@code nekojs:recipe_schemas} 扩展点收集。 */
    public interface Contributor extends NekoJSPlugin {

        /** 注册配方 schema 覆盖（同 {@code (namespace, type)} 首胜）。 */
        default void registerRecipeSchemas(RecipeSchemaRegister registry) {
        }
    }

    /** 累积器：同 {@code (namespace, type)} 首胜并告警；snapshot 后密封。 */
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

    /** 扩展点定义（由 {@link NekoBuiltinPointsPlugin} 清单注册）。 */
    public static final NekoPluginExtensionPoint<NekoJSPlugin, Bucket, Map<String, Map<String, RecipeTypeDefinition>>> POINT =
            NekoPluginExtensionPoint.<NekoJSPlugin, Bucket, Map<String, Map<String, RecipeTypeDefinition>>>builder(ID, NekoJSPlugin.class)
                    .merge(POLICY)
                    .initializer(context -> new Bucket())
                    .collector(NekoJSPlugin::registerRecipeSchemas)
                    .finish(Bucket::snapshot)
                    .build();
}
