// Implementation for 26.x; this file must not contain version guards again. The 1.21.1
// implementation is the file of the same name under versions/1.21.1/src; when you change the
// behavior here, update that file too.
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
 * Event object for {@code ClientEvents.hud}: draws the HUD after each frame's GUI render, using
 * the 26.x {@link GuiGraphicsExtractor} render-state model. All coordinates are GUI-scaled
 * pixels ({@code getWidth()/getHeight()} return the scaled screen size).
 *
 * <p>Colors are ARGB (for example {@code 0x80FF0000} is translucent red) and arrive as a
 * {@link Number} that is read bitwise as uint32 ({@link UiColor#argbBits}). A script literal
 * such as {@code 0xFF......} is an unsigned value of 2³¹ or more, and passing it straight into
 * a Java {@code int} parameter saturates it to {@code 0x7FFFFFFF} (semi-transparent white).
 * When a color is omitted, the current color set by {@code color()/resetColor()} is used
 * (opaque white by default).
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

    /** Test-only constructor: measures through the given adapter with no live client; paint calls
     * then fail fast. */
    PainterJS(GuiGraphicsExtractor guiGraphics, FontAdapter fontAdapter, float partialTick) {
        this.guiGraphics = guiGraphics;
        this.font = null;
        this.fontAdapter = fontAdapter;
        this.partialTick = partialTick;
    }

    /** Partial tick of the current render frame (0 to 1), used for smooth animation. */
    public float getPartialTick() {
        return partialTick;
    }

    /** Current client world tick count (use {@code worldTime % period} for repeating animations). */
    public long getWorldTime() {
        return Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0;
    }

    /** Screen width in GUI-scaled pixels. */
    public int getWidth() {
        return guiGraphics.guiWidth();
    }

    /** Screen height in GUI-scaled pixels. */
    public int getHeight() {
        return guiGraphics.guiHeight();
    }

    /** Sets the default color. The value is ARGB and arrives as a {@link Number} that
     * {@link UiColor#argbBits} reads bitwise as uint32; see the class comment. */
    public PainterJS color(Number color) {
        this.currentColor = UiColor.argbBits(color);
        return this;
    }

    /** Resets the default color to opaque white. */
    public PainterJS resetColor() {
        this.currentColor = 0xFFFFFFFF;
        return this;
    }

    /** Fills a solid rectangle. */
    public PainterJS rect(int x, int y, int width, int height) {
        guiGraphics.fill(x, y, x + width, y + height, currentColor);
        return this;
    }

    public PainterJS rect(int x, int y, int width, int height, Number color) {
        guiGraphics.fill(x, y, x + width, y + height, UiColor.argbBits(color));
        return this;
    }

    /** Draws a 1px rectangle border. */
    public PainterJS outline(int x, int y, int width, int height) {
        guiGraphics.outline(x, y, width, height, currentColor);
        return this;
    }

    public PainterJS outline(int x, int y, int width, int height, Number color) {
        guiGraphics.outline(x, y, width, height, UiColor.argbBits(color));
        return this;
    }

    /** Fills a rectangle with a vertical gradient. */
    public PainterJS gradient(int x, int y, int width, int height, Number colorTop, Number colorBottom) {
        guiGraphics.fillGradient(x, y, x + width, y + height,
                UiColor.argbBits(colorTop), UiColor.argbBits(colorBottom));
        return this;
    }

    /** Fills a rectangle with a horizontal gradient, interpolated column by column; very large
     * widths are expensive. */
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

    /** Draws left-aligned text. */
    public PainterJS text(String text, int x, int y) {
        guiGraphics.text(font, text, x, y, currentColor);
        return this;
    }

    public PainterJS text(String text, int x, int y, Number color) {
        guiGraphics.text(font, text, x, y, UiColor.argbBits(color));
        return this;
    }

    /** Draws horizontally centered text. */
    public PainterJS centerText(String text, int x, int y) {
        guiGraphics.centeredText(font, text, x, y, currentColor);
        return this;
    }

    public PainterJS centerText(String text, int x, int y, Number color) {
        guiGraphics.centeredText(font, text, x, y, UiColor.argbBits(color));
        return this;
    }

    /** Draws a region from a 256-pixel texture, preserving the legacy pixel-coordinate contract. */
    public PainterJS texture(String textureId, int x, int y, int width, int height) {
        return texture(textureId, x, y, 0, 0, width, height);
    }

    public PainterJS texture(String textureId, int x, int y, int u, int v, int width, int height) {
        return texture(textureId, x, y, u, v, width, height, 256, 256);
    }

    public PainterJS texture(String textureId, int x, int y, int u, int v, int width, int height,
            int textureWidth, int textureHeight) {
        if (textureWidth <= 0 || textureHeight <= 0) {
            throw new IllegalArgumentException("[NEKO-4026] Texture dimensions must be positive");
        }
        guiGraphics.blit(Identifier.parse(textureId), x, y, x + width, y + height,
                (float) u / textureWidth, ((float) u + width) / textureWidth,
                (float) v / textureHeight, ((float) v + height) / textureHeight);
        return this;
    }

    /** Draws the entire texture scaled to the requested rectangle. */
    public PainterJS textureFull(String textureId, int x, int y, int width, int height) {
        guiGraphics.blit(Identifier.parse(textureId), x, y, x + width, y + height, 0F, 1F, 0F, 1F);
        return this;
    }

    /** Draws an item icon (for example {@code Item.of('minecraft:diamond')}). */
    public PainterJS item(ItemStack stack, int x, int y) {
        guiGraphics.item(stack, x, y);
        return this;
    }

    /** Transform stack: saves the current transform; use it with {@code translate} and then
     * {@code pop}. */
    public PainterJS push() {
        guiGraphics.pose().pushMatrix();
        return this;
    }

    public PainterJS pop() {
        guiGraphics.pose().popMatrix();
        return this;
    }

    /** Translates later drawing in GUI coordinates: push first, translate, then pop. */
    public PainterJS translate(float x, float y) {
        guiGraphics.pose().translate(x, y);
        return this;
    }

    /** Clipping: limits later drawing to the rectangle. */
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
