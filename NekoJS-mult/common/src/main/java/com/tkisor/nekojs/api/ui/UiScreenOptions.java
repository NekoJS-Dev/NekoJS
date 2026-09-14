package com.tkisor.nekojs.api.ui;

/**
 * root 的 Screen 选项（jsx-ui-spec User Story 47/48、Implementation「Screen 行为」「暂停与替换」）。
 *
 * @param title            标题
 * @param pauseGame        是否暂停游戏
 * @param closeOnEscape    是否允许 Escape 关闭
 * @param closeOnExternal  外部 setScreen 替换时是否走同一 cleanup 路径
 * @param ownerThread      root 的 client owner thread；{@code null} 表示创建线程
 * @param safeArea         常驻 chrome 占用的安全区域（票 43 AC1）；{@code null} 表示无安全区域。
 *                         profile 判定使用「逻辑视口 - 安全区域」的可用布局区。
 * @param layoutCapabilities host 布局能力声明（票 43 AC1），如固定视口；{@code null} 表示无额外声明。
 *                        原生 GUI scale <b>不</b>在此声明——它不是 profile 的等价物。
 */
public record UiScreenOptions(
        String title,
        boolean pauseGame,
        boolean closeOnEscape,
        boolean closeOnExternal,
        Thread ownerThread,
        ViewportContext.SafeArea safeArea,
        java.util.Set<ViewportContext.Capability> layoutCapabilities
) {

    private static final UiScreenOptions DEFAULTS =
            new UiScreenOptions("", false, true, true, null, ViewportContext.SafeArea.none(), java.util.Set.of());

    /** 默认选项：不暂停、Escape 关闭、外部替换走同一 cleanup、无安全区域与额外布局能力。 */
    public static UiScreenOptions defaults() {
        return DEFAULTS;
    }

    /** 便捷构造：仅标题。 */
    public static UiScreenOptions titled(String title) {
        return new UiScreenOptions(title, false, true, true, null,
                ViewportContext.SafeArea.none(), java.util.Set.of());
    }

    public UiScreenOptions {
        title = title == null ? "" : title;
        safeArea = safeArea == null ? ViewportContext.SafeArea.none() : safeArea;
        layoutCapabilities = layoutCapabilities == null
                ? java.util.Set.of()
                : java.util.Set.copyOf(layoutCapabilities);
    }

    public UiScreenOptions withPauseGame(boolean value) {
        return new UiScreenOptions(title, value, closeOnEscape, closeOnExternal, ownerThread,
                safeArea, layoutCapabilities);
    }

    public UiScreenOptions withCloseOnEscape(boolean value) {
        return new UiScreenOptions(title, pauseGame, value, closeOnExternal, ownerThread,
                safeArea, layoutCapabilities);
    }

    public UiScreenOptions withOwnerThread(Thread value) {
        return new UiScreenOptions(title, pauseGame, closeOnEscape, closeOnExternal, value,
                safeArea, layoutCapabilities);
    }

    /** 声明常驻 chrome 的安全区域（进入 profile 判定）。 */
    public UiScreenOptions withSafeArea(ViewportContext.SafeArea value) {
        return new UiScreenOptions(title, pauseGame, closeOnEscape, closeOnExternal, ownerThread,
                value, layoutCapabilities);
    }

    /** 声明 host 布局能力（进入 profile 判定）。 */
    public UiScreenOptions withLayoutCapabilities(java.util.Set<ViewportContext.Capability> value) {
        return new UiScreenOptions(title, pauseGame, closeOnEscape, closeOnExternal, ownerThread,
                safeArea, value);
    }
}
