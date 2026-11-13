// 1.21.1 implementation. It pairs with the same-named 26.x file under src/ in the version
// tree: this file is that file evaluated for this node. Keep them in sync when 26.x changes.
package com.tkisor.nekojs.client.gui.components;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

public class NekoToast {
    private String message = "";
    private long expiryTime = 0;

    public void show(String msg) {
        this.message = msg.replaceAll("§[a-f0-8k-o]", "");
        this.expiryTime = System.currentTimeMillis() + 2000;
    }

    public void render(GuiGraphics g, Font font, int screenWidth, int screenHeight) {
        if (System.currentTimeMillis() >= expiryTime) return;
        float yAnim = Mth.clamp((2000f - (expiryTime - System.currentTimeMillis())) / 150f, 0, 1);
        int tw = font.width(message) + 20;
        int ty = screenHeight - 30 - (int) (15 * yAnim);
        int startX = screenWidth / 2 - tw / 2;
        int endX = screenWidth / 2 + tw / 2;
        int startY = ty;
        int endY = ty + 16;

        // background
        g.fill(startX, startY, endX, endY, 0xCC000000);

        // 1.21.1: four fill calls instead of outline
        g.fill(startX, startY, endX, startY + 1, 0xFF44FF44);           // top border
        g.fill(startX, endY - 1, endX, endY, 0xFF44FF44);               // bottom border
        g.fill(startX, startY + 1, startX + 1, endY - 1, 0xFF44FF44);   // left border
        g.fill(endX - 1, startY + 1, endX, endY - 1, 0xFF44FF44);       // right border
        g.drawCenteredString(font, message, screenWidth / 2, ty + 4, -1);
    }
}
