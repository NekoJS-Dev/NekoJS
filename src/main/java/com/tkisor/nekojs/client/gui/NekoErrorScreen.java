package com.tkisor.nekojs.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class NekoErrorScreen extends Screen {
    private final String scriptId;
    private final String errorDetails;

    public NekoErrorScreen(String scriptId, String errorDetails) {
        super(Component.literal("NekoJS 脚本错误"));
        this.scriptId = scriptId;
        this.errorDetails = errorDetails;
    }

    @Override
    protected void init() {
        super.init();

        MultiLineTextWidget textWidget = new MultiLineTextWidget(
                20, 40, Component.literal(errorDetails), this.font
        );
        textWidget.setMaxWidth(this.width - 40);
        this.addRenderableWidget(textWidget);

        this.addRenderableWidget(Button.builder(Component.literal("关闭"), btn -> this.onClose())
                .bounds(this.width / 2 - 50, this.height - 30, 100, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("复制堆栈"), btn -> {
            Minecraft.getInstance().keyboardHandler.setClipboard(errorDetails);
            btn.setMessage(Component.literal("已复制！"));
        }).bounds(this.width / 2 + 60, this.height - 30, 80, 20).build());
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {

        graphics.fillGradient(0, 0, this.width, this.height, 0xC0000000, 0xC0000000);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, "§c⚠ NekoJS 脚本运行异常", this.width / 2, 15, 0xFFFFFF);
        graphics.drawString(this.font, "目标脚本: §e" + scriptId, 20, 28, 0xFFFFFF);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean shouldCloseOnEsc() { return true; }
}