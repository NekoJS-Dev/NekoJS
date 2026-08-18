package com.tkisor.nekojs.core.module.cjs;

import java.util.List;

/**
 * CJS 模块的静态分析结果（第二阶段：Java AST/IR-backed CJS 基础设施）。
 *
 * <p>由 {@link CjsStaticAnalyzer} 在 prepare 阶段对源码做 token 级扫描产出，供：
 * <ul>
 *   <li>依赖图构建（静态字面量 require 依赖，reload 失效/热替换用）；</li>
 *   <li>ESM/CJS interop 的导出形状预判（module.exports 重赋值 vs exports.xxx 成员赋值、
 *       {@code __esModule} 互操作标记）；</li>
 *   <li>require.resolve 校验与未来 CJS record 缓存。</li>
 * </ul>
 *
 * <p>注意这是保守的静态近似，不是完整 AST：动态 require（非字面量参数）、条件分支内的
 * 导出赋值、箭头函数参数影子 require 等场景不做精确建模（见 {@link #shadowedRequire()}）。
 */
public record CjsModuleRecord(
        /** 字面量 require 依赖（去重保序）；{@code shadowedRequire} 时为空列表。 */
        List<String> staticDependencies,
        /** 检测到 {@code module.exports = ...}（整体重赋值，默认导出会被替换）。 */
        boolean assignsModuleExports,
        /** 检测到 {@code exports.xxx = ...} 或 {@code module.exports.xxx = ...}（成员赋值）。 */
        boolean assignsExportsMember,
        /** 检测到 {@code exports.__esModule = true} / {@code module.exports.__esModule = true}（Babel/TS 互操作标记）。 */
        boolean hasEsmInteropMarker,
        /** 检测到 require 被局部声明/赋值遮蔽（函数参数、let/const/var、赋值）——静态依赖不可靠。 */
        boolean shadowedRequire
) {
    public static final CjsModuleRecord EMPTY = new CjsModuleRecord(List.of(), false, false, false, false);

    public CjsModuleRecord {
        staticDependencies = List.copyOf(staticDependencies);
    }
}
