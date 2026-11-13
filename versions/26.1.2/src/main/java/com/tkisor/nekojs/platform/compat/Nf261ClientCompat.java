package com.tkisor.nekojs.platform.compat;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

/**
 * 26.1 implementation of {@link McClientCompat.Impl}. Minecraft exposes the screen and toast
 * manager accessors directly.
 * Registered through {@code META-INF/services/com.tkisor.nekojs.platform.compat.McClientCompat$Impl}.
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
