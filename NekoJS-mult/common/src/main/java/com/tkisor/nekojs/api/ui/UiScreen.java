package com.tkisor.nekojs.api.ui;

/**
 * UI screen 输入：render function + Screen 选项（jsx-ui-spec「root 输入」）。
 *
 * <p>render function 返回单个 VNode 或 fragment；每个 root 有自己的依赖订阅、
 * 提交队列、generation token、host tree 和 disposed 状态。
 */
public interface UiScreen {

    /** 渲染函数：返回 VNode、fragment 或空值。 */
    UiVNode render();

    /** Screen 选项。 */
    UiScreenOptions options();
}
