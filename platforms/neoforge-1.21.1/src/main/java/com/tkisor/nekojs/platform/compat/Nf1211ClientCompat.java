package com.tkisor.nekojs.platform.compat;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

/**
 * 1.21.1 侧 {@link McClientCompat.Impl}：toast 管理器是 {@code getToasts()}
 * （类型为 ToastComponent，与 26.x 的 ToastManager 不同名，因此以
 * {@code addOrUpdateSystemToast} 整操作下沉）；HoverEvent 用 Action 构造。
 * 注册：{@code META-INF/services/com.tkisor.nekojs.platform.compat.McClientCompat$Impl}。
 */
public final class Nf1211ClientCompat implements McClientCompat.Impl {

    @Override
    public Screen currentScreen() {
        return Minecraft.getInstance().screen;
    }

    @Override
    public void showScreen(Screen screen) {
        Minecraft.getInstance().setScreen(screen);
    }

    @Override
    public String renderBackendDescription() {
        return RenderSystem.getApiDescription();
    }

    @Override
    public void addOrUpdateSystemToast(SystemToast.SystemToastId id, Component title, Component description) {
        SystemToast.addOrUpdate(Minecraft.getInstance().getToasts(), id, title, description);
    }

    @Override
    public HoverEvent hoverEventShowText(Component text) {
        return new HoverEvent(HoverEvent.Action.SHOW_TEXT, text);
    }
}
