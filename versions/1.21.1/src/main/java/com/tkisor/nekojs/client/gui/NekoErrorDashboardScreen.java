// 1.21.1 adapter; all layout, state and platform actions are shared.
package com.tkisor.nekojs.client.gui;

import com.tkisor.nekojs.client.gui.dashboard.DashboardLayout;
import com.tkisor.nekojs.client.gui.dashboard.DashboardText;
import com.tkisor.nekojs.client.gui.dashboard.DashboardView;
import com.tkisor.nekojs.network.ErrorSummaryDTO;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.List;

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
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        view.paint(new DashboardView.Canvas() {
            @Override public void fill(DashboardLayout.Rect r, int color) {
                if (r.width() > 0 && r.height() > 0) graphics.fill(r.x(), r.y(), r.right(), r.bottom(), color);
            }
            @Override public void text(String text, int x, int y, int color) {
                graphics.drawString(font, Component.literal(text), x, y, color, false);
            }
            @Override public void clip(DashboardLayout.Rect r) { graphics.enableScissor(r.x(), r.y(), r.right(), r.bottom()); }
            @Override public void unclip() { graphics.disableScissor(); }
        }, mouseX, mouseY);
        if (searchBox.visible) {
            var r = view.layout().search(); graphics.enableScissor(r.x(), r.y(), r.right(), r.bottom());
            searchBox.render(graphics, mouseX, mouseY, partialTick); graphics.disableScissor();
        }
        String tooltip = view.tooltip(mouseX, mouseY);
        if (!tooltip.isEmpty()) setTooltipForNextRenderPass(Component.literal(DashboardText.display(tooltip)));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return true;
        DashboardView.Action action = view.click(mouseX, mouseY);
        draggingSearch = action == DashboardView.Action.SEARCH;
        perform(action);
        if (draggingSearch) searchBox.mouseClicked(mouseX, mouseY, button);
        refreshLocation(false);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (view.modal()) return true;
        if (draggingSearch && searchBox.isFocused()) searchBox.mouseDragged(mouseX, mouseY, button, dx, dy);
        else view.drag(mouseX, mouseY);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        view.release(); draggingSearch = false;
        searchBox.mouseReleased(mouseX, mouseY, button);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        view.scroll(mouseX, mouseY, -scrollY * (font.lineHeight + 1) * 3);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (view.modal()) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_KP_ENTER) { view.dismissDialog(); syncSearch(); }
            else if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_PAGE_UP) view.scrollFocused(-1, key == GLFW.GLFW_KEY_PAGE_UP);
            else if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_PAGE_DOWN) view.scrollFocused(1, key == GLFW.GLFW_KEY_PAGE_DOWN);
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB) { view.tab((modifiers & GLFW.GLFW_MOD_SHIFT) != 0); syncSearch(); return true; }
        if (searchBox.isFocused()) { searchBox.keyPressed(key, scanCode, modifiers); return true; }
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
    public boolean charTyped(char codePoint, int modifiers) {
        if (view.modal()) return true;
        // Handle the character, not GLFW_KEY_SLASH: opening search must not also insert '/'.
        if (codePoint == '/' && !searchBox.isFocused()) { view.focusSearch(); syncSearch(); return true; }
        if (searchBox.isFocused()) searchBox.charTyped(codePoint, modifiers);
        return true;
    }

}
