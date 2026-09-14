package com.tkisor.nekojs.api.ui;

/**
 * 把已提交 UI root 投影成原生 Screen 的平台边界（jsx-ui-spec「Screen 行为」「暂停与替换」）。
 *
 * <p>本接口只引用 {@link UiRootHandle}：原生 {@code Screen}、{@code GuiGraphics}、widget
 * 与输入对象全部由平台实现私有持有，common 侧看不到（jsx-ui-spec AC6「平台边界」）。
 * resize 由平台 Screen 直接驱动 root 的逻辑视口（见 {@code UiRoot} 的 Java 侧入口），
 * 因此这里不需要额外的视口成员。
 *
 * <p>生命周期：一个 root 对应一个原生 Screen；{@link #close(UiRootHandle)} 必须幂等，
 * 玩家主动关闭、外部 {@code setScreen} 替换、客户端退出与 reload 释放进入同一路径。
 * 全部方法只能在 root 的 client owner thread 上调用。
 */
public interface UiScreenHost {

    /** 创建并显示 root 的原生 Screen 投影。 */
    void open(UiRootHandle root);

    /** 释放 root 的原生 Screen；幂等。 */
    void close(UiRootHandle root);

    /** 原生 Screen 是否仍存活（外部替换、玩家关闭后为 {@code false}）。 */
    boolean isAlive(UiRootHandle root);

    /** 无宿主 Screen 时的空实现（纯逻辑/fake 环境）。 */
    UiScreenHost NONE = new UiScreenHost() {
        @Override
        public void open(UiRootHandle root) {
        }

        @Override
        public void close(UiRootHandle root) {
        }

        @Override
        public boolean isAlive(UiRootHandle root) {
            return false;
        }
    };
}
