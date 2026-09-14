package com.tkisor.nekojs.platform.compat;

import com.tkisor.nekojs.api.ui.UiResources;

/**
 * 26.2 侧 {@link McUiCompat.Impl}：交付真实的 JSX UI 宿主资源装配
 * （host Adapter + 原生 Screen + 文本测量 + 事件桥）。
 *
 * <p>注册：{@code META-INF/services/com.tkisor.nekojs.platform.compat.McUiCompat$Impl}。
 * 类名刻意与其它节点不同（drift 比较对不允许同名不同体）。
 *
 * <p>只有真实 Adapter 与客户端 smoke 完成后本节点才声明支持（jsx-ui-spec「平台边界」）；
 * 其余节点没有本文件也没有 service 条目，因此 {@code McUiCompat.isSupported()} 为 false，
 * capability 记为 {@code not verified}。
 */
public final class Nf262UiCompat implements McUiCompat.Impl {

    @Override
    public UiResources uiResources(int generation) {
        return new com.tkisor.nekojs.client.gui.Nf262UiResources(generation);
    }
}
