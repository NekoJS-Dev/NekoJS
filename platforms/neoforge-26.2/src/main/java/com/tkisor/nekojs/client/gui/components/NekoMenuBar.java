package com.tkisor.nekojs.client.gui.components;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import java.util.List;

public class NekoMenuBar {
    public record MenuCategory(String name, List<NekoContextMenu.MenuItem> items) {}

    private final List<MenuCategory> categories;
    private NekoContextMenu activeContextMenu = null;

    public NekoMenuBar(List<MenuCategory> categories) {
        this.categories = categories;
    }

    public void render(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, int startX, int startY) {
        int menuX = startX;
        for (MenuCategory cat : categories) {
            int w = font.width(cat.name());
            boolean hov = mouseX >= menuX && mouseX <= menuX + w + 8 && mouseY >= startY && mouseY <= startY + 14;
            if (hov && this.activeContextMenu == null) {
                g.fill(menuX, startY, menuX + w + 8, startY + 14, 0xFF3E3E42);
            }
            g.text(font, cat.name(), menuX + 4, startY + 3, 0xFFCCCCCC);
            menuX += w + 8;
        }
        if (this.activeContextMenu != null) {
            this.activeContextMenu.render(g, mouseX, mouseY);
        }
    }

    public boolean mouseClicked(double mx, double my, int button, Font font, int screenW, int screenH, int startX, int startY) {
        if (this.activeContextMenu != null) {
            this.activeContextMenu.mouseClicked(mx, my, button);
            this.activeContextMenu = null;
            return true;
        }
        if (my >= startY && my <= startY + 14) {
            int menuX = startX;
            for (MenuCategory cat : categories) {
                int w = font.width(cat.name()) + 8;
                if (mx >= menuX && mx <= menuX + w) {
                    this.activeContextMenu = new NekoContextMenu(font, menuX, startY + 14, screenW, screenH, cat.items());
                    return true;
                }
                menuX += w;
            }
        }
        return false;
    }

    public boolean keyPressed(KeyEvent event) {
        if (this.activeContextMenu != null && event.isEscape()) {
            this.activeContextMenu = null;
            return true;
        }
        return false;
    }

    public boolean isMenuOpen() {
        return this.activeContextMenu != null;
    }
}