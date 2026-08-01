package com.tkisor.nekojs.wrapper.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * {@code ClientEvents.hud} 事件对象：HUD 绘制（每帧 GUI 渲染后）。所有坐标以 GUI 缩放后的
 * 像素为单位（{@code getWidth()/getHeight()} 为缩放后屏幕尺寸）。
 *
 * <p>颜色参数为 ARGB int（如 {@code 0x80FF0000} = 半透明红）；省略颜色时使用
 * {@code color()/resetColor()} 设置的当前色（默认不透明白）。
 */
public class PainterJS {
    private final GuiGraphics guiGraphics;
    private final Font font;
    private int currentColor = 0xFFFFFFFF;

    public PainterJS(GuiGraphics guiGraphics) {
        this.guiGraphics = guiGraphics;
        this.font = Minecraft.getInstance().font;
    }

    /** 屏幕宽度（GUI 缩放后）。 */
    public int getWidth() {
        return Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }

    /** 屏幕高度（GUI 缩放后）。 */
    public int getHeight() {
        return Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }

    /** 设置默认颜色（ARGB），供省略颜色参数的绘制方法使用。 */
    public PainterJS color(int color) {
        this.currentColor = color;
        return this;
    }

    /** 重置默认颜色为不透明白。 */
    public PainterJS resetColor() {
        this.currentColor = 0xFFFFFFFF;
        return this;
    }

    /** 实心矩形。 */
    public PainterJS rect(int x, int y, int width, int height) {
        guiGraphics.fill(x, y, x + width, y + height, currentColor);
        return this;
    }

    public PainterJS rect(int x, int y, int width, int height, int color) {
        guiGraphics.fill(x, y, x + width, y + height, color);
        return this;
    }

    /** 1px 矩形边框（四条边线）。 */
    public PainterJS outline(int x, int y, int width, int height) {
        guiGraphics.hLine(x, x + width - 1, y, currentColor);
        guiGraphics.hLine(x, x + width - 1, y + height - 1, currentColor);
        guiGraphics.vLine(x, y, y + height - 1, currentColor);
        guiGraphics.vLine(x + width - 1, y, y + height - 1, currentColor);
        return this;
    }

    public PainterJS outline(int x, int y, int width, int height, int color) {
        guiGraphics.hLine(x, x + width - 1, y, color);
        guiGraphics.hLine(x, x + width - 1, y + height - 1, color);
        guiGraphics.vLine(x, y, y + height - 1, color);
        guiGraphics.vLine(x + width - 1, y, y + height - 1, color);
        return this;
    }

    /** 垂直渐变矩形。 */
    public PainterJS gradient(int x, int y, int width, int height, int colorTop, int colorBottom) {
        guiGraphics.fillGradient(x, y, x + width, y + height, colorTop, colorBottom);
        return this;
    }

    /** 文本（左对齐）。 */
    public PainterJS text(String text, int x, int y) {
        guiGraphics.drawString(font, text, x, y, currentColor);
        return this;
    }

    public PainterJS text(String text, int x, int y, int color) {
        guiGraphics.drawString(font, text, x, y, color);
        return this;
    }

    /** 文本（水平居中）。 */
    public PainterJS centerText(String text, int x, int y) {
        guiGraphics.drawCenteredString(font, text, x, y, currentColor);
        return this;
    }

    public PainterJS centerText(String text, int x, int y, int color) {
        guiGraphics.drawCenteredString(font, text, x, y, color);
        return this;
    }

    /** 贴图（完整纹理 id，如 {@code 'minecraft:textures/gui/icons.png'}），u/v 默认 0。 */
    public PainterJS texture(String textureId, int x, int y, int width, int height) {
        guiGraphics.blit(ResourceLocation.parse(textureId), x, y, 0, 0, width, height);
        return this;
    }

    /** 贴图（带纹理内偏移 u/v）。 */
    public PainterJS texture(String textureId, int x, int y, int u, int v, int width, int height) {
        guiGraphics.blit(ResourceLocation.parse(textureId), x, y, u, v, width, height);
        return this;
    }

    /** 物品图标（{@code Item.of('minecraft:diamond')}）。 */
    public PainterJS item(ItemStack stack, int x, int y) {
        guiGraphics.renderItem(stack, x, y);
        return this;
    }

    /** 变换栈：保存当前变换（配合 {@code translate} 使用）。 */
    public PainterJS push() {
        guiGraphics.pose().pushPose();
        return this;
    }

    public PainterJS pop() {
        guiGraphics.pose().popPose();
        return this;
    }

    /** 平移后续绘制（GUI 坐标，先 push 再 translate 最后 pop）。 */
    public PainterJS translate(float x, float y) {
        guiGraphics.pose().translate(x, y, 0);
        return this;
    }

    /** 裁剪：后续绘制限制在矩形区域内。 */
    public PainterJS scissor(int x, int y, int width, int height) {
        guiGraphics.enableScissor(x, y, x + width, y + height);
        return this;
    }

    public PainterJS resetScissor() {
        guiGraphics.disableScissor();
        return this;
    }
}
