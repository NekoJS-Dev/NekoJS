//? if neoforge {
package com.tkisor.nekojs.client.gui;

import com.tkisor.nekojs.api.ui.UiRootHandle;
import com.tkisor.nekojs.api.ui.UiScreenHost;
import com.tkisor.nekojs.api.ui.UiScreenOptions;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 26.2 的 {@link UiScreenHost} 实现：把 UI root 投影成真实原生 {@code Screen}
 * （jsx-ui-spec「Screen 行为」「暂停与替换」）。
 *
 * <p>映射是 <b>per-root 的弱引用表</b>，不是跨 generation 的静态 registry：root 释放、
 * Screen 被外部替换或客户端退出后条目自然消失；本类不缓存 generation、不持有脚本状态
 * （jsx-ui-spec「Runtime owner」）。
 *
 * <p>全部方法只在 client owner thread 上调用。
 */
public final class Nf262UiScreenHost implements UiScreenHost {

    private final Nf262UiHostAdapter adapter;
    private final Map<UiRootHandle, Nf262UiScreen> screens = new WeakHashMap<>();

    public Nf262UiScreenHost(Nf262UiHostAdapter adapter) {
        this.adapter = adapter;
    }

    @Override
    public void open(UiRootHandle root) {
        if (root == null || !root.isActive()) {
            return;
        }
        Nf262UiScreen existing = screens.get(root);
        if (existing != null && Minecraft.getInstance().gui.screen() == existing) {
            return;
        }
        UiScreenOptions options = root.options() == null ? UiScreenOptions.defaults() : root.options();
        Nf262UiScreen screen = new Nf262UiScreen(root, adapter, options);
        screens.put(root, screen);
        Minecraft.getInstance().gui.setScreen(screen);
    }

    @Override
    public void close(UiRootHandle root) {
        Nf262UiScreen screen = screens.remove(root);
        if (screen == null) {
            return;
        }
        // 外部替换/释放与玩家主动关闭走同一条幂等释放路径（jsx-ui-spec「暂停与替换」）。
        screen.closeFromScreen();
        if (Minecraft.getInstance().gui.screen() == screen) {
            Minecraft.getInstance().gui.setScreen(null);
        }
    }

    @Override
    public boolean isAlive(UiRootHandle root) {
        Nf262UiScreen screen = screens.get(root);
        return screen != null && screen == Minecraft.getInstance().gui.screen();
    }

    /** 当前显示中的 JSX Screen（诊断用）。 */
    public Screen currentScreen() {
        Screen screen = Minecraft.getInstance().gui.screen();
        return screen instanceof Nf262UiScreen ? screen : null;
    }
}
//?}