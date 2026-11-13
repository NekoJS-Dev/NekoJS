//? if <26 {
/*package com.tkisor.nekojs.platform.compat;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

/^*
 * 1.21.1 side of {@link McClientCompat.Impl}. The toast manager comes from {@code getToasts()},
 * whose type is ToastComponent, a different name from 26.x's ToastManager, so the whole operation
 * moves down into {@code addOrUpdateSystemToast}. HoverEvent is built with the Action constructor.
 * Registration: {@code META-INF/services/com.tkisor.nekojs.platform.compat.McClientCompat$Impl}.
^/
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
 *///?}
