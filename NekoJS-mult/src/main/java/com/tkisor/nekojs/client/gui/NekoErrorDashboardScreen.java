//? if neoforge && >=26 {
package com.tkisor.nekojs.client.gui;

import com.tkisor.nekojs.client.gui.dashboard.DashboardLayout;
import com.tkisor.nekojs.client.gui.dashboard.DashboardText;
import com.tkisor.nekojs.client.gui.dashboard.DashboardView;
import com.tkisor.nekojs.network.ErrorSummaryDTO;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.List;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/** Minecraft API adapter for the approved blue-a-2 dashboard. */
public final class NekoErrorDashboardScreen extends AbstractErrorDashboardScreen {
    private NekoErrorDashboardScreen() { super(); }
    public static NekoErrorDashboardScreen create(List<ErrorSummaryDTO> errors) {
        var screen = new NekoErrorDashboardScreen();
        screen.updateErrors(errors);
        return screen;
    }
    @Override public void updateErrors(List<ErrorSummaryDTO> errors) { super.updateErrors(errors); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        view.paint(new DashboardView.Canvas() {
            @Override public void fill(DashboardLayout.Rect r, int color) {
                if (r.width() > 0 && r.height() > 0) graphics.fill(r.x(), r.y(), r.right(), r.bottom(), color);
            }
            @Override public void text(String text, int x, int y, int color) {
                graphics.text(font, Component.literal(text), x, y, color, false);
            }
            @Override public void clip(DashboardLayout.Rect r) { graphics.enableScissor(r.x(), r.y(), r.right(), r.bottom()); }
            @Override public void unclip() { graphics.disableScissor(); }
        }, mouseX, mouseY);
        if (searchBox.visible) {
            var r = view.layout().search(); graphics.enableScissor(r.x(), r.y(), r.right(), r.bottom());
            searchBox.extractRenderState(graphics, mouseX, mouseY, partialTick); graphics.disableScissor();
        }
        String tooltip = view.tooltip(mouseX, mouseY);
        if (!tooltip.isEmpty()) graphics.setTooltipForNextFrame(font, Component.literal(DashboardText.display(tooltip)), mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return true;
        DashboardView.Action action = view.click(event.x(), event.y());
        draggingSearch = action == DashboardView.Action.SEARCH;
        perform(action);
        if (draggingSearch) searchBox.mouseClicked(event, doubleClick);
        refreshLocation(false);
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (view.modal()) return true;
        if (draggingSearch && searchBox.isFocused()) searchBox.mouseDragged(event, dx, dy);
        else view.drag(event.x(), event.y());
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        view.release(); draggingSearch = false;
        searchBox.mouseReleased(event);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        view.scroll(mouseX, mouseY, -scrollY * (font.lineHeight + 1) * 3);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (view.modal()) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_KP_ENTER) { view.dismissDialog(); syncSearch(); }
            else if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_PAGE_UP) view.scrollFocused(-1, key == GLFW.GLFW_KEY_PAGE_UP);
            else if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_PAGE_DOWN) view.scrollFocused(1, key == GLFW.GLFW_KEY_PAGE_DOWN);
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB) { view.tab((event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0); syncSearch(); return true; }
        if (searchBox.isFocused()) { searchBox.keyPressed(event); return true; }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_KP_ENTER) { perform(view.focus()); return true; }
        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
            int direction = key == GLFW.GLFW_KEY_UP ? -1 : 1;
            if (view.focus() == DashboardView.Action.LIST && !view.collapsed()) { view.moveSelection(direction); refreshLocation(false); }
            else view.scrollFocused(direction, false);
            return true;
        }
        if (key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_PAGE_DOWN) view.scrollFocused(key == GLFW.GLFW_KEY_PAGE_UP ? -1 : 1, true);
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (view.modal()) return true;
        // Handle the character, not GLFW_KEY_SLASH: opening search must not also insert '/'.
        if (event.codepoint() == '/' && !searchBox.isFocused()) { view.focusSearch(); syncSearch(); return true; }
        if (searchBox.isFocused()) searchBox.charTyped(event);
        return true;
    }

}
//?}
