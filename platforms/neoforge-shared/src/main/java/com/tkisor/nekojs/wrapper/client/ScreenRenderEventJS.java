package com.tkisor.nekojs.wrapper.client;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Return;
import net.minecraft.client.gui.screens.Screen;

/**
 * {@code ClientEvents.screenRender} 事件对象：当前打开界面（Screen）渲染后的绘制
 * 上下文——{@code painter} 与 hud 同款绘制 API，另带界面与鼠标信息。
 */
@Doc("Event fired after the currently open screen renders (ClientEvents.screenRender).")
@Doc("Carries a painter with the same drawing API as the hud event, plus screen and mouse info.")
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
    @Doc("Gets the drawing helper for this frame.")
    @Return("a PainterJS with the same API as the hud event painter")
    public PainterJS getPainter() {
        return painter;
    }

    /** 当前打开的界面对象。 */
    @Doc("Gets the screen currently being rendered.")
    @Return("the rendered Screen instance; covers any screen, including title and pause screens")
    public Screen getScreen() {
        return screen;
    }

    /** 界面标题（如 {@code 'Chest'}）。 */
    @Doc("Gets the displayed title of the open screen.")
    @Return("the title as plain text, e.g. 'Chest'")
    public String getScreenTitle() {
        return screen.getTitle().getString();
    }

    /** 鼠标 X 坐标（GUI 缩放后坐标）。 */
    @Doc("Gets the mouse X position.")
    @Return("mouse X in GUI-scaled coordinates")
    public int getMouseX() {
        return mouseX;
    }

    /** 鼠标 Y 坐标（GUI 缩放后坐标）。 */
    @Doc("Gets the mouse Y position.")
    @Return("mouse Y in GUI-scaled coordinates")
    public int getMouseY() {
        return mouseY;
    }
}
