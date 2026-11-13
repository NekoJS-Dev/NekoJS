package com.tkisor.nekojs.wrapper.client;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Return;
import net.minecraft.client.gui.screens.Screen;

/**
 * Event object for {@code ClientEvents.screenRender}: the drawing context after the currently
 * open Screen renders. Its {@code painter} has the same drawing API as the hud event and the
 * object also carries the screen and mouse position.
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

    /** The painter; same API as {@code ClientEvents.hud}, including partialTick. */
    @Doc("Gets the drawing helper for this frame.")
    @Return("a PainterJS with the same API as the hud event painter")
    public PainterJS getPainter() {
        return painter;
    }

    /** The screen that is currently open. */
    @Doc("Gets the screen currently being rendered.")
    @Return("the rendered Screen instance; covers any screen, including title and pause screens")
    public Screen getScreen() {
        return screen;
    }

    /** The screen title (for example {@code 'Chest'}). */
    @Doc("Gets the displayed title of the open screen.")
    @Return("the title as plain text, e.g. 'Chest'")
    public String getScreenTitle() {
        return screen.getTitle().getString();
    }

    /** Mouse X in GUI-scaled coordinates. */
    @Doc("Gets the mouse X position.")
    @Return("mouse X in GUI-scaled coordinates")
    public int getMouseX() {
        return mouseX;
    }

    /** Mouse Y in GUI-scaled coordinates. */
    @Doc("Gets the mouse Y position.")
    @Return("mouse Y in GUI-scaled coordinates")
    public int getMouseY() {
        return mouseY;
    }
}
