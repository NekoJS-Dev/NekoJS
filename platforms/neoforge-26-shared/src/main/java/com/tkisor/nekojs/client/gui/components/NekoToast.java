package com.tkisor.nekojs.client.gui.components;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

public class NekoToast {
    private String message = "";
    private long expiryTime = 0;

    public void show(String msg) {
        this.message = msg;
        this.expiryTime = System.currentTimeMillis() + 2000;
    }

    public void render(GuiGraphicsExtractor g, Font font, int screenWidth, int screenHeight) {
        if (System.currentTimeMillis() >= expiryTime) return;
        float yAnim = Mth.clamp((2000f - (expiryTime - System.currentTimeMillis())) / 150f, 0, 1);
        int tw = font.width(message) + 20;
        int ty = screenHeight - 30 - (int) (15 * yAnim);
        g.fill(screenWidth / 2 - tw / 2, ty, screenWidth / 2 + tw / 2, ty + 16, 0xCC000000);
        g.outline(screenWidth / 2 - tw / 2, ty, tw, 16, 0xFF44FF44);
        g.centeredText(font, message, screenWidth / 2, ty + 4, -1);
    }
}