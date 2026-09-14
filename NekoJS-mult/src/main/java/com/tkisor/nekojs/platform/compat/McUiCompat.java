//? if neoforge {
package com.tkisor.nekojs.platform.compat;

import com.tkisor.nekojs.api.ui.UiResources;

import java.util.ServiceLoader;

/**
 * 客户端 JSX UI 宿主资源的跨版本装配门面（jsx-ui-spec「平台边界」）。
 *
 * <p>本门面住在共享树、<b>签名零 Minecraft/loader 类型</b>：返回值是 common 侧窄契约
 * {@link UiResources}。原生 Adapter 与 Screen 实现由各节点提供（
 * {@code Nf262UiResources} 等），经 {@code META-INF/services} 由 ServiceLoader 解析，
 * 因此服务端加载本类不会触碰任何客户端专属类型。
 *
 * <p>没有提供者的节点显式返回 {@code null}：{@code IPlatform.uiResources()} 为 null 时
 * common 侧走 {@code UnavailableUiFacade}，脚本 {@code Ui.open(...)} 抛
 * {@code UNSUPPORTED_CAPABILITY}——能力缺失是显式的，不是静默 no-op
 * （jsx-ui-spec「平台边界」）。
 */
public final class McUiCompat {

    private static final Impl IMPL = ServiceLoader.load(Impl.class, McUiCompat.class.getClassLoader())
            .findFirst()
            .orElse(Impl.UNSUPPORTED);

    private McUiCompat() {}

    /** 是否为客户端提供 UI 宿主资源实现。 */
    public static boolean isSupported() {
        return IMPL != Impl.UNSUPPORTED;
    }

    /** 装配 CLIENT generation 的 UI 宿主资源；无实现返回 {@code null}。 */
    public static UiResources uiResources(int generation) {
        return IMPL.uiResources(generation);
    }

    /** 各节点实现（只有 26.2 有真实实现；其余节点显式 not verified）。 */
    public interface Impl {
        /** 返回 null 表示本节点尚未交付真实 Adapter（capability 三态语义）。 */
        UiResources uiResources(int generation);

        Impl UNSUPPORTED = generation -> null;
    }
}
//?}
