// 1.21.1 implementation. It pairs with the same-named 26.x file under src/ in the version
// tree: this file is that file evaluated for this node. Keep them in sync when 26.x changes.
package com.tkisor.nekojs.client.gui.components;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
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

    public void render(GuiGraphics g, int mx, int my) {
        g.fill(x, y, x + width, y + height, 0xFF18181B);
        g.fill(x, y, x + width, y + 1, 0xFF3F3F46); // top
        g.fill(x, y + height - 1, x + width, y + height, 0xFF3F3F46); // bottom
        g.fill(x, y + 1, x + 1, y + height - 1, 0xFF3F3F46); // left
        g.fill(x + width - 1, y + 1, x + width, y + height - 1, 0xFF3F3F46); // right
        for (int i = 0; i < items.size(); i++) {
            int iy = y + 3 + i * 18;
            boolean h = mx >= x && mx <= x + width && my >= iy && my < iy + 18;
            if (h) g.fill(x + 2, iy, x + width - 2, iy + 18, 0xFF27272A);
            g.drawString(font, items.get(i).label(), x + 8, iy + 5, h ? -1 : 0xFFA1A1AA, false);
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
