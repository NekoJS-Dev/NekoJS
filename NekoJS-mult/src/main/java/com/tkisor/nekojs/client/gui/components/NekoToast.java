package com.tkisor.nekojs.client.gui.components;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

public class NekoToast {
    private String message = "";
    private long expiryTime = 0;

    public void show(String msg) {
//? if >=26 {
        this.message = msg;
//?} else {
/*        this.message = msg.replaceAll("§[a-f0-8k-o]", "");
*///?}
        this.expiryTime = System.currentTimeMillis() + 2000;
    }

    public void render(GuiGraphicsExtractor g, Font font, int screenWidth, int screenHeight) {
        if (System.currentTimeMillis() >= expiryTime) return;
        float yAnim = Mth.clamp((2000f - (expiryTime - System.currentTimeMillis())) / 150f, 0, 1);
        int tw = font.width(message) + 20;
        int ty = screenHeight - 30 - (int) (15 * yAnim);
//? if >=26 {
        g.fill(screenWidth / 2 - tw / 2, ty, screenWidth / 2 + tw / 2, ty + 16, 0xCC000000);
        g.outline(screenWidth / 2 - tw / 2, ty, tw, 16, 0xFF44FF44);
//?} else {
/*        int startX = screenWidth / 2 - tw / 2;
        int endX = screenWidth / 2 + tw / 2;
        int startY = ty;
        int endY = ty + 16;

        // 背景
        g.fill(startX, startY, endX, endY, 0xCC000000);

        // 1.21.1: 用 4 条 fill 替代 outline
        g.fill(startX, startY, endX, startY + 1, 0xFF44FF44);           // 上边框
        g.fill(startX, endY - 1, endX, endY, 0xFF44FF44);               // 下边框
        g.fill(startX, startY + 1, startX + 1, endY - 1, 0xFF44FF44);   // 左边框
        g.fill(endX - 1, startY + 1, endX, endY - 1, 0xFF44FF44);       // 右边框
*///?}
        g.centeredText(font, message, screenWidth / 2, ty + 4, -1);
    }
}
