// 1.21.1 节点专有变体（DEVEX-ROADMAP 档 1 整文件拆分）：主干已 26.x 基准化，本文件为 1.21.1 的
// 完整实现（构造性变换）；主干行为变更时须同步本文件。
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

        // 背景
        g.fill(startX, startY, endX, endY, 0xCC000000);

        // 1.21.1: 用 4 条 fill 替代 outline
        g.fill(startX, startY, endX, startY + 1, 0xFF44FF44);           // 上边框
        g.fill(startX, endY - 1, endX, endY, 0xFF44FF44);               // 下边框
        g.fill(startX, startY + 1, startX + 1, endY - 1, 0xFF44FF44);   // 左边框
        g.fill(endX - 1, startY + 1, endX, endY - 1, 0xFF44FF44);       // 右边框
        g.drawCenteredString(font, message, screenWidth / 2, ty + 4, -1);
    }
}
