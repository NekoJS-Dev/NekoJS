package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.TypeDocCatalogEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 内置扩展点 {@code nekojs:type_docs}（ADR-0001 自包含 Point 文件）。
 * merge 策略 {@link MergePolicy#append}（文档条目列表收集，无键冲突）。
 *
 * <p>累积器 {@link Bucket} 与产物快照 {@link TypeDocsSnapshot} 同时被孪生点
 * {@code nekojs:node_type_docs}（{@link NodeTypeDocsPoint}）复用——两点共享
 * 注册面类型 {@link TypeDocsRegister}，仅收集的插件集合不同。
 */
public final class TypeDocsPoint {

    /** 扩展点 id（observable 契约）。 */
    public static final String ID = "nekojs:type_docs";

    private TypeDocsPoint() {
    }

    /** 显式声明形态（与直接覆写 NekoJSPlugin 对应钩子等价收集）：插件被 {@code nekojs:type_docs} 扩展点收集。 */
    public interface Contributor extends NekoJSPlugin {

        /** 注册类型文档（为脚本侧类型补充说明/示例，进入 probe 输出与补全）。 */
        default void registerTypeDocs(TypeDocsRegister registry) {
        }
    }

    /** type_docs / node_type_docs 两点的产物：优先级排序后的类型文档与手写声明快照。 */
    record TypeDocsSnapshot(List<TypeDocCatalogEntry> docs, List<ManualDeclarationCatalogEntry> manualDeclarations) {
        TypeDocsSnapshot {
            docs = docs.stream().sorted(Comparator.comparingInt(TypeDocCatalogEntry::priority)).toList();
            manualDeclarations = manualDeclarations.stream()
                    .sorted(Comparator.comparingInt(ManualDeclarationCatalogEntry::priority)).toList();
        }
    }

    /** 累积器：文档与手写声明两个列表；snapshot 后密封。 */
    static final class Bucket implements TypeDocsRegister, Sealable {
        private final List<TypeDocCatalogEntry> docs = new ArrayList<>();
        private final List<ManualDeclarationCatalogEntry> manualDeclarations = new ArrayList<>();
        private boolean sealed;

        private void checkSealed() {
            if (sealed) {
                throw new IllegalStateException("扩展点已 finish，累积器密封，不可再收集");
            }
        }

        @Override
        public void register(TypeDocCatalogEntry entry) {
            checkSealed();
            docs.add(java.util.Objects.requireNonNull(entry, "entry"));
        }

        @Override
        public void registerManualDeclaration(ManualDeclarationCatalogEntry entry) {
            checkSealed();
            manualDeclarations.add(java.util.Objects.requireNonNull(entry, "entry"));
        }

        TypeDocsSnapshot snapshot() {
            sealed = true;
            return new TypeDocsSnapshot(List.copyOf(docs), List.copyOf(manualDeclarations));
        }

        @Override
        public void seal() {
            this.sealed = true;
        }
    }

    /** 扩展点定义（由 {@link NekoBuiltinPointsPlugin} 清单注册）。 */
    public static final NekoPluginExtensionPoint<NekoJSPlugin, Bucket, TypeDocsSnapshot> POINT =
            NekoPluginExtensionPoint.<NekoJSPlugin, Bucket, TypeDocsSnapshot>builder(ID, NekoJSPlugin.class)
                    .merge(MergePolicy.append())
                    .initializer(context -> new Bucket())
                    .collector(NekoJSPlugin::registerTypeDocs)
                    .finish(Bucket::snapshot)
                    .build();
}
