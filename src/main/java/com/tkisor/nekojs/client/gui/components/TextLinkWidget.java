package com.tkisor.nekojs.client.gui.components;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

public class TextLinkWidget extends AbstractButton {
    private final Runnable onPressAction;
    private final Font font;
    private final int normalColor;
    private final int hoverColor;

    public TextLinkWidget(int x, int y, Component message, Font font, int normalColor, int hoverColor, Runnable onPressAction) {
        super(x, y, font.width(message), font.lineHeight, message);
        this.font = font;
        this.normalColor = normalColor;
        this.hoverColor = hoverColor;
        this.onPressAction = onPressAction;
    }

    // 🌟 完美实现 1.21.2 的全新按键处理机制
    @Override
    public void onPress(InputWithModifiers inputWithModifiers) {
        this.onPressAction.run();
    }

    // 🌟 完美实现 1.21.2 的全新渲染提取机制
    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        boolean hovered = this.isHoveredOrFocused();
        graphics.text(this.font, this.getMessage(), this.getX(), this.getY(), hovered ? this.hoverColor : this.normalColor);

        // 悬停时画下划线
        if (hovered) {
            graphics.fill(this.getX(), this.getY() + this.font.lineHeight, this.getX() + this.width, this.getY() + this.font.lineHeight + 1, this.hoverColor);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        // 保持空实现即可
    }
}