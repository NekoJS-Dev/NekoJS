package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;

/**
 * 内置扩展点 {@code nekojs:node_type_docs}（ADR-0001 自包含 Point 文件）——
 * {@code nekojs:type_docs}（{@link TypeDocsPoint}）的孪生点：共享累积器
 * {@link TypeDocsPoint.Bucket} 与产物 {@link TypeDocsPoint.TypeDocsSnapshot}，
 * 仅收集的插件集合（Contributor）不同。merge 策略同为 append。
 */
public final class NodeTypeDocsPoint {

    /** 扩展点 id（observable 契约）。 */
    public static final String ID = "nekojs:node_type_docs";

    private NodeTypeDocsPoint() {
    }

    /** 显式声明形态（与直接覆写 NekoJSPlugin 对应钩子等价收集）：插件被 {@code nekojs:node_type_docs} 扩展点收集。 */
    public interface Contributor extends NekoJSPlugin {

        /**
         * 注册 node 模块的补全声明（{@code declare module 'node:xxx' {...}}）。
         * 与 {@code node_modules} 扩展点配对：前者提供模块实现，本方法提供类型声明。
         */
        default void registerNodeTypeDocs(TypeDocsRegister registry) {
        }
    }

    /** 扩展点定义（由 {@link NekoBuiltinPointsPlugin} 清单注册）。 */
    public static final NekoPluginExtensionPoint<NekoJSPlugin, TypeDocsPoint.Bucket, TypeDocsPoint.TypeDocsSnapshot> POINT =
            NekoPluginExtensionPoint.<NekoJSPlugin, TypeDocsPoint.Bucket, TypeDocsPoint.TypeDocsSnapshot>builder(ID, NekoJSPlugin.class)
                    .merge(MergePolicy.append())
                    .initializer(context -> new TypeDocsPoint.Bucket())
                    .collector(NekoJSPlugin::registerNodeTypeDocs)
                    .finish(TypeDocsPoint.Bucket::snapshot)
                    .build();
}
