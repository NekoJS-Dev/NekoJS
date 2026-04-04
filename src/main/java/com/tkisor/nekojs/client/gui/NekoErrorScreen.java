package com.tkisor.nekojs.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

import java.io.File;

public class NekoErrorScreen extends Screen {
    private final Screen parentScreen;
    private final String scriptId;
    private final String errorDetails;
    private String toastMessage = "";
    private long toastTime = 0;

    // 🌟 核心：用滚动列表替换 MultiLineTextWidget，解决爆栈溢出问题
    private StackTraceList stackList;

    public NekoErrorScreen(String scriptId, String errorDetails) {
        this(null, scriptId, errorDetails);
    }

    public NekoErrorScreen(Screen parentScreen, String scriptId, String errorDetails) {
        super(Component.literal("Traceback"));
        this.parentScreen = parentScreen;
        this.scriptId = scriptId;
        this.errorDetails = errorDetails;
    }

    @Override
    protected void init() {
        super.init();

        int cX = 15, cY = 42, cW = this.width - 30, cH = this.height - 80;

        // 🌟 初始化滚动列表，按行切割塞进去
        this.stackList = new StackTraceList(this.minecraft, cW, cH, cY, 12);
        errorDetails.replace("\t", "    ").lines().forEach(line -> {
            this.stackList.addEntry(new TextLineEntry(line));
        });
        this.addRenderableWidget(this.stackList);

        // 🌟 你的 4 个底部按钮完美保留
        int bw = 70, sp = 5, y = this.height - 30;
        int sx = this.width / 2 - (bw * 4 + sp * 3) / 2;

        this.addRenderableWidget(Button.builder(Component.literal("返回"), b -> this.onClose()).bounds(sx, y, bw, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("§e定位脚本"), b -> {
            File f = new File(Minecraft.getInstance().gameDirectory, "nekojs/" + scriptId);
            Util.getPlatform().openFile(f);
            showToast("§a尝试打开文件");
        }).bounds(sx + (bw + sp), y, bw, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("§b查看日志"), b -> {
            File log = new File(Minecraft.getInstance().gameDirectory, "logs/latest.log");
            Util.getPlatform().openFile(log);
            showToast("§a打开 latest.log");
        }).bounds(sx + (bw + sp) * 2, y, bw, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("复制堆栈"), b -> {
            Minecraft.getInstance().keyboardHandler.setClipboard(errorDetails);
            showToast("§a已存入剪贴板");
        }).bounds(sx + (bw + sp) * 3, y, bw, 20).build());
    }

    private void showToast(String msg) {
        this.toastMessage = msg;
        this.toastTime = System.currentTimeMillis() + 2000;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(0, 0, this.width, this.height, 0xFF0A0A0B, 0xFF121214);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, 35, 0x80000000);
        graphics.fill(0, 35, this.width, 36, 0x40FFFFFF);
        graphics.text(this.font, "§cNEKO§fJS §7Traceback", 20, 13, -1);

        int cX = 15, cY = 42, cW = this.width - 30, cH = this.height - 80;

        // 先画黑底
        graphics.fill(cX, cY, cX + cW, cY + cH, 0xFF1E1E1E);

        // 调用 super 绘制滚动列表文字
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        // 再画上层的红色饰条和边框（覆盖掉滚动时超出边缘的文字，达到剪裁效果）
        graphics.fill(cX, cY, cX + 2, cY + cH, 0xFFE5534B);
        graphics.outline(cX, cY, cW, cH, 0xFF333333);

        // 🌟 丝滑滑出的 Toast 动画
        long timeRemaining = toastTime - System.currentTimeMillis();
        if (timeRemaining > 0) {
            float alphaAnim = Mth.clamp(timeRemaining / 300f, 0, 1);
            float yAnim = Mth.clamp((2000f - timeRemaining) / 150f, 0, 1);
            int alphaBase = (int) (alphaAnim * 255);

            int tw = this.font.width(toastMessage) + 20;
            int ty = cY + 10 + (int)(10 * (1 - yAnim)); // 从上往下滑动

            graphics.fill(this.width/2 - tw/2, ty, this.width/2 + tw/2, ty + 16, (int)(alphaAnim * 0xEE) << 24);
            graphics.outline(this.width/2 - tw/2, ty, tw, 16, alphaBase << 24 | 0x44FF44);
            graphics.centeredText(this.font, toastMessage, this.width / 2, ty + 4, alphaBase << 24 | 0xFFFFFF);
        }
    }

    @Override public void onClose() {
        if (this.parentScreen != null) this.minecraft.setScreen(this.parentScreen);
        else super.onClose();
    }

    // ================= 内部类：终端堆栈滚动列表 =================
    private class StackTraceList extends ObjectSelectionList<TextLineEntry> {
        public StackTraceList(Minecraft mc, int w, int h, int y, int ih) {
            super(mc, w, h, y, ih);
            this.setX(15); // 偏移到控制台内部
        }
        @Override public int getRowWidth() { return this.width - 20; }
        @Override protected int scrollBarX() { return this.getX() + this.width - 8; }
        @Override protected void extractListBackground(GuiGraphicsExtractor g) {}
        @Override protected void extractListSeparators(GuiGraphicsExtractor g) {}

        public int addEntry(TextLineEntry textLineEntry) {
            return super.addEntry(textLineEntry);
        }
    }

    // 内部类：单行文本
    private class TextLineEntry extends ObjectSelectionList.Entry<TextLineEntry> {
        private final String lineText;
        public TextLineEntry(String line) { this.lineText = line; }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mx, int my, boolean isHovered, float pt) {
            int color = 0xFFCCCCCC;
            // 🌟 轻量级代码高亮：Java堆栈变暗，Exception变红
            if (lineText.trim().startsWith("at ")) color = 0xFF888888;
            if (lineText.contains("Error:") || lineText.contains("Exception")) color = 0xFFE5534B;

            graphics.text(NekoErrorScreen.this.font, "§7" + lineText, this.getX() + 10, this.getY() + 2, color);
        }
        @Override public Component getNarration() { return Component.empty(); }
    }
}