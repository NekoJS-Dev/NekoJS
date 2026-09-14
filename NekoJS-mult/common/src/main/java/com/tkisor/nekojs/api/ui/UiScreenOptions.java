package com.tkisor.nekojs.api.ui;

/**
 * root 的 Screen 选项（jsx-ui-spec User Story 47/48、Implementation「Screen 行为」「暂停与替换」）。
 *
 * @param title            标题
 * @param pauseGame        是否暂停游戏
 * @param closeOnEscape    是否允许 Escape 关闭
 * @param closeOnExternal  外部 setScreen 替换时是否走同一 cleanup 路径
 * @param ownerThread      root 的 client owner thread；{@code null} 表示创建线程
 */
public record UiScreenOptions(
        String title,
        boolean pauseGame,
        boolean closeOnEscape,
        boolean closeOnExternal,
        Thread ownerThread
) {

    private static final UiScreenOptions DEFAULTS =
            new UiScreenOptions("", false, true, true, null);

    /** 默认选项：不暂停、Escape 关闭、外部替换走同一 cleanup。 */
    public static UiScreenOptions defaults() {
        return DEFAULTS;
    }

    /** 便捷构造：仅标题。 */
    public static UiScreenOptions titled(String title) {
        return new UiScreenOptions(title, false, true, true, null);
    }

    public UiScreenOptions {
        title = title == null ? "" : title;
    }

    public UiScreenOptions withPauseGame(boolean value) {
        return new UiScreenOptions(title, value, closeOnEscape, closeOnExternal, ownerThread);
    }

    public UiScreenOptions withCloseOnEscape(boolean value) {
        return new UiScreenOptions(title, pauseGame, value, closeOnExternal, ownerThread);
    }

    public UiScreenOptions withOwnerThread(Thread value) {
        return new UiScreenOptions(title, pauseGame, closeOnEscape, closeOnExternal, value);
    }
}
