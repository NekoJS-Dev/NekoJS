package com.tkisor.nekojs.platform.compat;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

/**
 * 26.2 implementation of {@link McClientCompat.Impl}. The screen and toast manager accessors moved
 * to {@code Minecraft#gui}, and the RenderSystem backend description was renamed to
 * {@code getBackendDescription()}.
 * Registered through {@code META-INF/services/com.tkisor.nekojs.platform.compat.McClientCompat$Impl}.
 */
public final class Nf262ClientCompat implements McClientCompat.Impl {

    @Override
    public Screen currentScreen() {
        return Minecraft.getInstance().gui.screen();
    }

    @Override
    public void showScreen(Screen screen) {
        Minecraft.getInstance().gui.setScreen(screen);
    }

    @Override
    public String renderBackendDescription() {
        return RenderSystem.getBackendDescription();
    }

    @Override
    public void addOrUpdateSystemToast(SystemToast.SystemToastId id, Component title, Component description) {
        SystemToast.addOrUpdate(Minecraft.getInstance().gui.toastManager(), id, title, description);
    }

    @Override
    public HoverEvent hoverEventShowText(Component text) {
        return new HoverEvent.ShowText(text);
    }

}
