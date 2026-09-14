package com.tkisor.nekojs.api.ui;

/**
 * host 侧可检查状态（jsx-ui-spec「转换产物可检查」的可观察子集）。
 *
 * <p>由 {@link UiRootHandle#inspection()} 产出；只暴露公开调用者 Interface 需要的事实：
 * 已提交 host 树、最终矩形、裁剪区域、解析后的布局约束与事件绑定摘要。
 *
 * @param committedRoot   最近一次成功提交的 host 树根，可为 {@code null}（无有效树）
 * @param paintOperations 最近一次绘制的可观察顺序（节点路径）
 * @param clipRects       每个绘制节点的最终裁剪矩形
 */
public record UiHostInspection(
        UiHostNode committedRoot,
        java.util.List<String> paintOperations,
        java.util.Map<String, UiRect> clipRects
) {
    public UiHostInspection {
        paintOperations = java.util.List.copyOf(paintOperations == null ? java.util.List.of() : paintOperations);
        clipRects = java.util.Map.copyOf(clipRects == null ? java.util.Map.of() : clipRects);
    }

    /** 无已提交树。 */
    public static UiHostInspection empty() {
        return new UiHostInspection(null, java.util.List.of(), java.util.Map.of());
    }

    /** 是否有可显示的有效树。 */
    public boolean hasCommittedTree() {
        return committedRoot != null;
    }
}
