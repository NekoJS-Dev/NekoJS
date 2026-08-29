package com.tkisor.nekojs.platform.compat;

import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.util.ServiceLoader;

/**
 * 客户端侧的跨版本编译期符号差异门面（W5/A8 step 2）。
 *
 * <p>本门面住在 neoforge-shared（1.21.1 与 26.x 都编译它），覆盖三类差异：
 * <ul>
 *   <li>26.1 → 26.2 机械改名：{@code Minecraft#screen / setScreen} →
 *       {@code Gui#screen() / setScreen()}、{@code RenderSystem#getApiDescription()} →
 *       {@code getBackendDescription()}；</li>
 *   <li>26.x → 1.21.1 形状差：toast 管理器 26.x 是 {@code ToastManager}、1.21.1 是
 *       {@code ToastComponent}（类型名不同，无法进共享签名——改为推送
 *       {@link #addOrUpdateSystemToast} 整个操作）；HoverEvent 的 show-text 构造
 *       26.x 是 {@code HoverEvent.ShowText}、1.21.1 是 {@code new HoverEvent(Action, ...)}。</li>
 * </ul>
 * 实现在各版本模块（{@code Nf1211ClientCompat} / {@code Nf261ClientCompat} /
 * {@code Nf262ClientCompat}，类名刻意不同——drift 比较对不允许同名不同体），经
 * {@code META-INF/services} 由 ServiceLoader 在首次取用时解析。
 *
 * <p><strong>只在客户端代码路径引用</strong>：签名含客户端专属类型，专用服务器上加载
 * 本类会 NoClassDefFoundError。
 */
public final class McClientCompat {

    private static final Impl IMPL = ServiceLoader.load(Impl.class, McClientCompat.class.getClassLoader())
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                    "No McClientCompat.Impl provider on classpath: expected a per-version impl "
                            + "in neoforge-1.21.1/neoforge-26.1/neoforge-26.2 (META-INF/services)"));

    private McClientCompat() {}

    public static Impl get() {
        return IMPL;
    }

    public interface Impl {
        Screen currentScreen();

        void showScreen(Screen screen);

        /** 26.x：{@code RenderSystem.getApiDescription()}；26.2 起改名 getBackendDescription。 */
        String renderBackendDescription();

        /** SystemToast.addOrUpdate 的版本无关形式（toast 管理器类型两端不同，整个操作下沉到实现）。 */
        void addOrUpdateSystemToast(SystemToast.SystemToastId id, Component title, Component description);

        /** show-text HoverEvent 的版本无关构造（26.x HoverEvent.ShowText vs 1.21.1 Action 构造）。 */
        HoverEvent hoverEventShowText(Component text);
    }
}
