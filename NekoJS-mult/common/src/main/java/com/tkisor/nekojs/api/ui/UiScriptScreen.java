package com.tkisor.nekojs.api.ui;

/**
 * 由脚本侧 render function 驱动的 {@link UiScreen} 实现边界。
 *
 * <p>render function 返回单个 VNode 或 fragment；每个 root 有自己的依赖订阅、
 * 提交队列、generation token、host tree 和 disposed 状态（jsx-ui-spec「root 输入」）。
 * 由引擎侧实现把 render function 句柄转换为候选 VNode。
 */
public interface UiScriptScreen extends UiScreen {

    /** 脚本侧 render function（opaque 句柄，由平台/引擎桥接提供）。 */
    Object renderFunction();
}
