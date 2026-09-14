package com.tkisor.nekojs.api.ui;

/**
 * 响应式布局契约的只读观察者（jsx-ui-spec 68/69，票 43 AC1/AC5）。
 *
 * <p>由 {@link UiRootHandle#viewportState()} 返回。它是一个<b>查询 Interface</b>：脚本拿到的是
 * 一个按类型反射进 managed contract 的对象，用来读取「当前观察到的档位、逻辑视口、可用布局区
 * 与最终解析样式」，而不是创建 root 或改变布局。这与把布局算法暴露给脚本是两回事——本接口
 * 不提供任何写操作，也没有独立的状态副本：
 *
 * <ul>
 *   <li>{@link #viewport()} 直接返回 root 已算出的 {@link ProjectedViewport} 事实；</li>
 *   <li>{@link #profile()} 是该事实上的判定结果；</li>
 *   <li>{@link #resolvedStyles()} 直接返回最近一次 measure/arrange 解析出的
 *       {@link UiLayoutSpec}（已应用 profile 覆盖与设计坐标缩放）。</li>
 * </ul>
 *
 * <p>因此它是「事实视图」而不是第二份布局真相源：三个方法都只读已有结果。
 */
public interface UiViewportState {

    /** 当前响应式视口事实（档位、逻辑视口、可用布局区、安全区域、原生 scale 与布局代数）。 */
    ProjectedViewport viewport();

    /**
     * 最近一次布局解析出的每个可见节点样式（节点路径 → 最终解析布局约束）。
     *
     * <p>返回不可变副本；未提交过有效树时返回空映射。
     */
    java.util.Map<String, UiLayoutSpec> resolvedStyles();
}