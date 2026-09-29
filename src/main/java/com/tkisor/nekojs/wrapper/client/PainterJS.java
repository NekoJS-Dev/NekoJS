// 26.x 实现，本文件不应再出现版本守卫。1.21.1 的实现是 versions/1.21.1/src 下的同名文件，
// 改本文件行为时须同步它。
package com.tkisor.nekojs.wrapper.client;

import com.tkisor.nekojs.api.ui.FontAdapter;
import com.tkisor.nekojs.api.ui.TextLayouter;
import com.tkisor.nekojs.api.ui.UiColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * {@code ClientEvents.hud} 事件对象：HUD 绘制（每帧 GUI 渲染后，26.x 的
 * {@link GuiGraphicsExtractor} 渲染状态模型）。所有坐标以 GUI 缩放后的像素为单位
 * （{@code getWidth()/getHeight()} 为缩放后屏幕尺寸）。
 *
 * <p>颜色参数为 ARGB（如 {@code 0x80FF0000} = 半透明红），以 {@link Number} 接收并按
 * uint32 读位（{@link UiColor#argbBits}）：脚本字面量 {@code 0xFF......} 是 ≥ 2³¹ 的
 * 无符号数，直接落入 Java {@code int} 参数会被引擎饱和成 {@code 0x7FFFFFFF}（半透明白，
 * 缺陷 D6）。省略颜色时使用 {@code color()/resetColor()} 设置的当前色（默认不透明白）。
 */
public class PainterJS {
    private final GuiGraphicsExtractor guiGraphics;
    private final Font font;
    private final FontAdapter fontAdapter;
    private final float partialTick;
    private int currentColor = 0xFFFFFFFF;

    public PainterJS(GuiGraphicsExtractor guiGraphics) {
        this(guiGraphics, 0F);
    }

    public PainterJS(GuiGraphicsExtractor guiGraphics, float partialTick) {
        this.guiGraphics = guiGraphics;
        this.font = Minecraft.getInstance().font;
        this.fontAdapter = new McFontAdapter(this.font);
        this.partialTick = partialTick;
    }

    /** Test seam: measures through the given adapter without a live client; paint calls then fail fast. */
    PainterJS(GuiGraphicsExtractor guiGraphics, FontAdapter fontAdapter, float partialTick) {
        this.guiGraphics = guiGraphics;
        this.font = null;
        this.fontAdapter = fontAdapter;
        this.partialTick = partialTick;
    }

    /** 当前渲染帧的部分插值（0~1），用于平滑动画。 */
    public float getPartialTick() {
        return partialTick;
    }

    /** 当前客户端世界 tick 数（周期动画：{@code worldTime % 周期}）。 */
    public long getWorldTime() {
        return Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0;
    }

    /** 屏幕宽度（GUI 缩放后）。 */
    public int getWidth() {
        return guiGraphics.guiWidth();
    }

    /** 屏幕高度（GUI 缩放后）。 */
    public int getHeight() {
        return guiGraphics.guiHeight();
    }

    /** 设置默认颜色（ARGB，{@link Number} 收值经 {@link UiColor#argbBits} 按 uint32 读位，见类注释）。 */
    public PainterJS color(Number color) {
        this.currentColor = UiColor.argbBits(color);
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

    public PainterJS rect(int x, int y, int width, int height, Number color) {
        guiGraphics.fill(x, y, x + width, y + height, UiColor.argbBits(color));
        return this;
    }

    /** 1px 矩形边框。 */
    public PainterJS outline(int x, int y, int width, int height) {
        guiGraphics.outline(x, y, x + width, y + height, currentColor);
        return this;
    }

    public PainterJS outline(int x, int y, int width, int height, Number color) {
        guiGraphics.outline(x, y, x + width, y + height, UiColor.argbBits(color));
        return this;
    }

    /** 垂直渐变矩形。 */
    public PainterJS gradient(int x, int y, int width, int height, Number colorTop, Number colorBottom) {
        guiGraphics.fillGradient(x, y, x + width, y + height,
                UiColor.argbBits(colorTop), UiColor.argbBits(colorBottom));
        return this;
    }

    /** 水平渐变矩形（逐列插值，宽度不宜过大）。 */
    public PainterJS gradientH(int x, int y, int width, int height, Number colorLeft, Number colorRight) {
        if (width <= 0) {
            return this;
        }
        int left = UiColor.argbBits(colorLeft);
        int right = UiColor.argbBits(colorRight);
        float max = width > 1 ? width - 1 : 1;
        for (int i = 0; i < width; i++) {
            guiGraphics.fill(x + i, y, x + i + 1, y + height, lerpColor(left, right, i / max));
        }
        return this;
    }

    private static int lerpColor(int from, int to, float t) {
        int a = (int) (((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
        int r = (int) (((from >>> 16) & 0xFF) + (((to >>> 16) & 0xFF) - ((from >>> 16) & 0xFF)) * t);
        int g = (int) (((from >>> 8) & 0xFF) + (((to >>> 8) & 0xFF) - ((from >>> 8) & 0xFF)) * t);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** 文本（左对齐）。 */
    public PainterJS text(String text, int x, int y) {
        guiGraphics.text(font, text, x, y, currentColor);
        return this;
    }

    public PainterJS text(String text, int x, int y, Number color) {
        guiGraphics.text(font, text, x, y, UiColor.argbBits(color));
        return this;
    }

    /** 文本（水平居中）。 */
    public PainterJS centerText(String text, int x, int y) {
        guiGraphics.centeredText(font, text, x, y, currentColor);
        return this;
    }

    public PainterJS centerText(String text, int x, int y, Number color) {
        guiGraphics.centeredText(font, text, x, y, UiColor.argbBits(color));
        return this;
    }

    /** 贴图（完整纹理 id，如 {@code 'minecraft:textures/gui/icons.png'}），u/v 默认 0。 */
    public PainterJS texture(String textureId, int x, int y, int width, int height) {
        guiGraphics.blit(Identifier.parse(textureId), x, y, width, height, 0, 0, width, height);
        return this;
    }

    /** 贴图（带纹理内偏移 u/v）。 */
    public PainterJS texture(String textureId, int x, int y, int u, int v, int width, int height) {
        guiGraphics.blit(Identifier.parse(textureId), x, y, width, height, u, v, width, height);
        return this;
    }

    /** 物品图标（{@code Item.of('minecraft:diamond')}）。 */
    public PainterJS item(ItemStack stack, int x, int y) {
        guiGraphics.item(stack, x, y);
        return this;
    }

    /** 变换栈：保存当前变换（配合 {@code translate} 使用）。 */
    public PainterJS push() {
        guiGraphics.pose().pushMatrix();
        return this;
    }

    public PainterJS pop() {
        guiGraphics.pose().popMatrix();
        return this;
    }

    /** 平移后续绘制（GUI 坐标，先 push 再 translate 最后 pop）。 */
    public PainterJS translate(float x, float y) {
        guiGraphics.pose().translate(x, y);
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

    /** Text width in GUI pixels measured with the current Minecraft font; null or empty text measures 0. */
    public int textWidth(String text) {
        return fontAdapter.stringWidth(text);
    }

    /**
     * Wraps text into lines that each fit {@code maxWidth}, sharing the JSX UI layout
     * algorithm ({@code TextLayouter}). Explicit {@code \n} also breaks lines.
     */
    public java.util.List<String> wrapText(String text, int maxWidth) {
        return TextLayouter.layout(fontAdapter, text, maxWidth, true, TextLayouter.Truncation.OFF).lines();
    }

    /** Line height of the current Minecraft font in GUI pixels. */
    public int lineHeight() {
        return fontAdapter.lineHeight();
    }

}
