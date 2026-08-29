package com.tkisor.nekojs.platform.compat;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

/**
 * 26.1 侧 {@link McClientCompat.Impl}：Minecraft 直接持有 screen / toast manager 访问器。
 * 注册：{@code META-INF/services/com.tkisor.nekojs.platform.compat.McClientCompat$Impl}。
 */
public final class Nf261ClientCompat implements McClientCompat.Impl {

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
        SystemToast.addOrUpdate(Minecraft.getInstance().getToastManager(), id, title, description);
    }

    @Override
    public HoverEvent hoverEventShowText(Component text) {
        return new HoverEvent.ShowText(text);
    }
}
