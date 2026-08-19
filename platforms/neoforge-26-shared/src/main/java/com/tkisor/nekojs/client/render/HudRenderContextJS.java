package com.tkisor.nekojs.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * {@code ClientEvents.hudRender} 回调上下文（26.x 的 {@link GuiGraphicsExtractor}
 * 渲染状态模型）：携带 partialTick 与缩放后屏幕尺寸，并提供常用绘制助手
 * （移植自 Katton 的 HUD draw helpers，命名对齐 {@code PainterJS}；
 * {@code drawText}/{@code fillRect}/{@code drawTexture} 为任务规格别名）。
 *
 * <p>颜色参数为 ARGB int（如 {@code 0x80FF0000} = 半透明红）。
 */
public class HudRenderContextJS {
    private final GuiGraphicsExtractor graphics;
    private final Font font;
    private final float partialTick;
    private int currentColor = 0xFFFFFFFF;

    public HudRenderContextJS(GuiGraphicsExtractor graphics, float partialTick) {
        this.graphics = graphics;
        this.font = Minecraft.getInstance().font;
        this.partialTick = partialTick;
    }

    /** 当前渲染帧的部分插值（0~1），用于平滑动画。 */
    public float getPartialTick() {
        return partialTick;
    }

    /** 屏幕宽度（GUI 缩放后）。 */
    public int getWidth() {
        return graphics.guiWidth();
    }

    /** 屏幕高度（GUI 缩放后）。 */
    public int getHeight() {
        return graphics.guiHeight();
    }

    /** 原始图形对象（26.x {@link GuiGraphicsExtractor}），供脚本直接调用未包装的能力。 */
    public GuiGraphicsExtractor graphics() {
        return graphics;
    }

    /** 设置默认颜色（ARGB），供省略颜色参数的绘制方法使用。 */
    public HudRenderContextJS color(int color) {
        this.currentColor = color;
        return this;
    }

    /** 重置默认颜色为不透明白。 */
    public HudRenderContextJS resetColor() {
        this.currentColor = 0xFFFFFFFF;
        return this;
    }

    /** 文本（左对齐）。 */
    public HudRenderContextJS text(String text, int x, int y) {
        graphics.text(font, text, x, y, currentColor);
        return this;
    }

    public HudRenderContextJS text(String text, int x, int y, int color) {
        graphics.text(font, text, x, y, color);
        return this;
    }

    /** 任务规格别名：{@link #text(String, int, int, int)}。 */
    public HudRenderContextJS drawText(String text, int x, int y, int color) {
        return text(text, x, y, color);
    }

    /** 文本（水平居中）。 */
    public HudRenderContextJS centerText(String text, int x, int y) {
        graphics.centeredText(font, text, x, y, currentColor);
        return this;
    }

    public HudRenderContextJS centerText(String text, int x, int y, int color) {
        graphics.centeredText(font, text, x, y, color);
        return this;
    }

    /** 实心矩形（宽高语义）。 */
    public HudRenderContextJS rect(int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, currentColor);
        return this;
    }

    public HudRenderContextJS rect(int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + height, color);
        return this;
    }

    /** 任务规格别名：{@link #rect(int, int, int, int, int)}。 */
    public HudRenderContextJS fillRect(int x, int y, int width, int height, int color) {
        return rect(x, y, width, height, color);
    }

    /** 1px 矩形边框。 */
    public HudRenderContextJS outline(int x, int y, int width, int height) {
        graphics.outline(x, y, x + width, y + height, currentColor);
        return this;
    }

    public HudRenderContextJS outline(int x, int y, int width, int height, int color) {
        graphics.outline(x, y, x + width, y + height, color);
        return this;
    }

    /** 垂直渐变矩形。 */
    public HudRenderContextJS gradient(int x, int y, int width, int height, int colorTop, int colorBottom) {
        graphics.fillGradient(x, y, x + width, y + height, colorTop, colorBottom);
        return this;
    }

    /**
     * 贴图（完整纹理 id，如 {@code 'minecraft:textures/gui/icons.png'}），u/v 为纹理内偏移。
     */
    public HudRenderContextJS texture(String textureId, int x, int y, int width, int height, int u, int v) {
        graphics.blit(Identifier.parse(textureId), x, y, width, height, u, v, width, height);
        return this;
    }

    /** 贴图（整张纹理，u/v 为 0）。 */
    public HudRenderContextJS texture(String textureId, int x, int y, int width, int height) {
        graphics.blit(Identifier.parse(textureId), x, y, width, height, 0, 0, width, height);
        return this;
    }

    /** 任务规格别名：{@link #texture(String, int, int, int, int, int, int)}。 */
    public HudRenderContextJS drawTexture(String textureId, int x, int y, int width, int height, int u, int v) {
        return texture(textureId, x, y, width, height, u, v);
    }

    /** 物品图标（{@code Item.of('minecraft:diamond')}）。 */
    public HudRenderContextJS item(ItemStack stack, int x, int y) {
        graphics.item(stack, x, y);
        return this;
    }

    /** 裁剪：后续绘制限制在矩形区域内。 */
    public HudRenderContextJS scissor(int x, int y, int width, int height) {
        graphics.enableScissor(x, y, x + width, y + height);
        return this;
    }

    public HudRenderContextJS resetScissor() {
        graphics.disableScissor();
        return this;
    }
}
