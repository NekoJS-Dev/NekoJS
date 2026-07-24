package com.tkisor.nekojs.client.gui.components;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.List;

public class NekoContextMenu {
    private final int x, y, width, height;
    private final List<MenuItem> items;
    private final Font font;

    public record MenuItem(String label, Runnable action) {}

    public NekoContextMenu(Font font, int sx, int sy, int screenWidth, int screenHeight, List<MenuItem> items) {
        this.font = font;
        this.items = items;
        this.height = items.size() * 18 + 6;
        int maxW = 120;
        for (MenuItem item : items) {
            maxW = Math.max(maxW, font.width(item.label()) + 20);
        }
        this.width = maxW;
        this.x = Math.min(sx, screenWidth - width - 5);
        this.y = Math.min(sy, screenHeight - height - 5);
    }

    public void render(GuiGraphicsExtractor g, int mx, int my) {
        g.fill(x, y, x + width, y + height, 0xFF18181B);
        g.outline(x, y, width, height, 0xFF3F3F46);
        for (int i = 0; i < items.size(); i++) {
            int iy = y + 3 + i * 18;
            boolean h = mx >= x && mx <= x + width && my >= iy && my < iy + 18;
            if (h) g.fill(x + 2, iy, x + width - 2, iy + 18, 0xFF27272A);
            g.text(font, items.get(i).label(), x + 8, iy + 5, h ? -1 : 0xFFA1A1AA);
        }
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && mx >= x && mx <= x + width && my >= y && my <= y + height) {
            int index = ((int) my - y - 3) / 18;
            if (index >= 0 && index < items.size()) items.get(index).action().run();
            return true;
        }
        return false;
    }
}