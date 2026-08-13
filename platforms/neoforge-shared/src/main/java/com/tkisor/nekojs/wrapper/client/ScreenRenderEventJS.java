package com.tkisor.nekojs.wrapper.client;

import net.minecraft.client.gui.screens.Screen;

/**
 * {@code ClientEvents.screenRender} 事件对象：当前打开界面（Screen）渲染后的绘制
 * 上下文——{@code painter} 与 hud 同款绘制 API，另带界面与鼠标信息。
 */
public class ScreenRenderEventJS {
    private final PainterJS painter;
    private final Screen screen;
    private final int mouseX;
    private final int mouseY;

    public ScreenRenderEventJS(PainterJS painter, Screen screen, int mouseX, int mouseY) {
        this.painter = painter;
        this.screen = screen;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
    }

    /** 绘制器（与 {@code ClientEvents.hud} 同款 API，含 partialTick）。 */
    public PainterJS getPainter() {
        return painter;
    }

    /** 当前打开的界面对象。 */
    public Screen getScreen() {
        return screen;
    }

    /** 界面标题（如 {@code 'Chest'}）。 */
    public String getScreenTitle() {
        return screen.getTitle().getString();
    }

    public int getMouseX() {
        return mouseX;
    }

    public int getMouseY() {
        return mouseY;
    }
}
