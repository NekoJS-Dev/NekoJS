package com.tkisor.nekojs.api.ui;

import java.util.List;
import java.util.Map;

/**
 * host 侧可检查状态（jsx-ui-spec「转换产物可检查」的可观察子集）。
 *
 * <p>由 {@link UiRootHandle#inspection()} 产出；只暴露公开调用者 Interface 需要的事实：
 * 已提交 host 树、最终矩形、裁剪区域、每个可见节点的最终解析样式、解析出的 Viewport Profile
 * 与溢出诊断（票 43 AC5）。
 *
 * <p>{@code resolvedStyles} 与 {@code overflow} 用节点路径作键，是「布局事实」而不是第二份
 * 布局真相源：它们只报告 measure/arrange 已经算出的结果。
 *
 * @param committedRoot   最近一次成功提交的 host 树根，可为 {@code null}（无有效树）
 * @param paintOperations 最近一次绘制的可观察顺序（节点路径）
 * @param clipRects       每个绘制节点的最终裁剪矩形
 * @param resolvedStyles  每个可见节点的最终解析布局约束（profile 覆盖已应用）
 * @param overflow        每个节点相对其容器可用区的溢出量（无溢出为 0）
 * @param viewport        本次布局使用的响应式视口事实，可为 {@code null}（尚未判定）
 * @param layoutRevisions 布局代数：每完成一次 measure/arrange 提交 +1，可观察「resize 只重排」
 */
public record UiHostInspection(
        UiHostNode committedRoot,
        List<String> paintOperations,
        Map<String, UiRect> clipRects,
        Map<String, UiLayoutSpec> resolvedStyles,
        Map<String, UiOverflow> overflow,
        ProjectedViewport viewport,
        long layoutRevisions
) {
    public UiHostInspection {
        paintOperations = List.copyOf(paintOperations == null ? List.of() : paintOperations);
        clipRects = Map.copyOf(clipRects == null ? Map.of() : clipRects);
        resolvedStyles = Map.copyOf(resolvedStyles == null ? Map.of() : resolvedStyles);
        overflow = Map.copyOf(overflow == null ? Map.of() : overflow);
    }

    /** 无已提交树、无布局事实。 */
    public static UiHostInspection empty() {
        return new UiHostInspection(null, List.of(), Map.of(), Map.of(), Map.of(), null, 0);
    }

    /** 是否有可显示的有效树。 */
    public boolean hasCommittedTree() {
        return committedRoot != null;
    }

    /** 解析出的 Viewport Profile；尚未判定时为 {@code null}。 */
    public ViewportProfile profile() {
        return viewport == null ? null : viewport.profile();
    }

    /** 是否检测到任何溢出。 */
    public boolean hasOverflow() {
        return overflow.values().stream().anyMatch(UiOverflow::any);
    }

    /** 只替换视口事实与布局代数，保留已提交树、绘制顺序与裁剪（resize 重排路径用）。 */
    public UiHostInspection forViewport(ProjectedViewport nextViewport, long revisions) {
        return new UiHostInspection(committedRoot, paintOperations, clipRects,
                resolvedStyles, overflow, nextViewport, revisions);
    }

    /** 提交新树并携带本次布局解析出的事实。 */
    public UiHostInspection withCommittedTree(UiHostNode root, Map<String, UiLayoutSpec> styles,
                                              Map<String, UiOverflow> layoutOverflow,
                                              ProjectedViewport nextViewport, long revisions) {
        return new UiHostInspection(root, paintOperations, clipRects, styles, layoutOverflow,
                nextViewport, revisions);
    }
}
