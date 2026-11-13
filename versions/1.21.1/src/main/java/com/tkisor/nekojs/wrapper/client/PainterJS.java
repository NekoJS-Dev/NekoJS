// 1.21.1 implementation, paired with the same-named 26.x file under the version tree src/. Its
// content is that file as evaluated at this node. Keep this file in sync when the 26.x side changes.
package com.tkisor.nekojs.wrapper.client;

import com.tkisor.nekojs.api.ui.FontAdapter;
import com.tkisor.nekojs.api.ui.TextLayouter;
import com.tkisor.nekojs.api.ui.UiColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Event object for {@code ClientEvents.hud}: HUD drawing after each frame's GUI render, using the
 * 26.x {@link GuiGraphics} render state model. All coordinates are in GUI-scaled pixels
 * ({@code getWidth()}/{@code getHeight()} return the scaled screen size).
 *
 * <p>Color parameters are ARGB (e.g. {@code 0x80FF0000} is half-transparent red). They are taken as
 * {@link Number} and read as uint32 bits ({@link UiColor#argbBits}): the script literal
 * {@code 0xFF......} is an unsigned value >= 2³¹, and passing it straight to a Java {@code int}
 * parameter makes the engine saturate it to {@code 0x7FFFFFFF} (half-transparent white, defect D6).
 * Leaving the color out uses the current color set by {@code color()}/{@code resetColor()} (opaque
 * white by default).
 */
public class PainterJS {
    private final GuiGraphics guiGraphics;
    private final Font font;
    private final FontAdapter fontAdapter;
    private final float partialTick;
    private int currentColor = 0xFFFFFFFF;


    public PainterJS(GuiGraphics guiGraphics) {
        this(guiGraphics, 0F);
    }

    public PainterJS(GuiGraphics guiGraphics, float partialTick) {
        this.guiGraphics = guiGraphics;
        this.font = Minecraft.getInstance().font;
        this.fontAdapter = new McFontAdapter(this.font);
        this.partialTick = partialTick;
    }

    /** Test-only: measures text through the given adapter without a live client; paint calls then fail fast. */
    PainterJS(GuiGraphics guiGraphics, FontAdapter fontAdapter, float partialTick) {
        this.guiGraphics = guiGraphics;
        this.font = null;
        this.fontAdapter = fontAdapter;
        this.partialTick = partialTick;
    }

    /** Partial tick of the current render frame (0 to 1), for smooth animation. */
    public float getPartialTick() {
        return partialTick;
    }

    /** Current client world tick count (for periodic animation: {@code worldTime % period}). */
    public long getWorldTime() {
        return Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0;
    }

    /** Screen width after GUI scaling. */
    public int getWidth() {
        return Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }

    /** Screen height after GUI scaling. */
    public int getHeight() {
        return Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }

    /** Sets the default color (ARGB; a {@link Number} is read as uint32 bits through
     * {@link UiColor#argbBits}, see the class comment). */
    public PainterJS color(Number color) {
        this.currentColor = UiColor.argbBits(color);
        return this;
    }

    /** Resets the default color to opaque white. */
    public PainterJS resetColor() {
        this.currentColor = 0xFFFFFFFF;
        return this;
    }

    /** Filled rectangle. */
    public PainterJS rect(int x, int y, int width, int height) {
        guiGraphics.fill(x, y, x + width, y + height, currentColor);
        return this;
    }

    public PainterJS rect(int x, int y, int width, int height, Number color) {
        guiGraphics.fill(x, y, x + width, y + height, UiColor.argbBits(color));
        return this;
    }

    /** 1px rectangle border. */
    public PainterJS outline(int x, int y, int width, int height) {
        guiGraphics.hLine(x, x + width - 1, y, currentColor);
        guiGraphics.hLine(x, x + width - 1, y + height - 1, currentColor);
        guiGraphics.vLine(x, y, y + height - 1, currentColor);
        guiGraphics.vLine(x + width - 1, y, y + height - 1, currentColor);
        return this;
    }

    public PainterJS outline(int x, int y, int width, int height, Number color) {
        int argb = UiColor.argbBits(color);
        guiGraphics.hLine(x, x + width - 1, y, argb);
        guiGraphics.hLine(x, x + width - 1, y + height - 1, argb);
        guiGraphics.vLine(x, y, y + height - 1, argb);
        guiGraphics.vLine(x + width - 1, y, y + height - 1, argb);
        return this;
    }

    /** Rectangle with a vertical gradient. */
    public PainterJS gradient(int x, int y, int width, int height, Number colorTop, Number colorBottom) {
        guiGraphics.fillGradient(x, y, x + width, y + height,
                UiColor.argbBits(colorTop), UiColor.argbBits(colorBottom));
        return this;
    }

    /** Rectangle with a horizontal gradient (interpolated per column, so keep the width moderate). */
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

    /** Text, left aligned. */
    public PainterJS text(String text, int x, int y) {
        guiGraphics.drawString(font, text, x, y, currentColor);
        return this;
    }

    public PainterJS text(String text, int x, int y, Number color) {
        guiGraphics.drawString(font, text, x, y, UiColor.argbBits(color));
        return this;
    }

    /** Text, horizontally centered. */
    public PainterJS centerText(String text, int x, int y) {
        guiGraphics.drawCenteredString(font, text, x, y, currentColor);
        return this;
    }

    public PainterJS centerText(String text, int x, int y, Number color) {
        guiGraphics.drawCenteredString(font, text, x, y, UiColor.argbBits(color));
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
        guiGraphics.blit(ResourceLocation.parse(textureId), x, y, (float) u, (float) v,
                width, height, textureWidth, textureHeight);
        return this;
    }

    /** Draws the entire texture scaled to the requested rectangle. */
    public PainterJS textureFull(String textureId, int x, int y, int width, int height) {
        if (width == 0 || height == 0) return this;
        guiGraphics.blit(ResourceLocation.parse(textureId), x, y, 0F, 0F, width, height, width, height);
        return this;
    }

    /** Item icon (e.g. from {@code Item.of('minecraft:diamond')}). */
    public PainterJS item(ItemStack stack, int x, int y) {
        guiGraphics.renderItem(stack, x, y);
        return this;
    }

    /** Pose stack: saves the current transform (use with {@code translate}). */
    public PainterJS push() {
        guiGraphics.pose().pushPose();
        return this;
    }

    public PainterJS pop() {
        guiGraphics.pose().popPose();
        return this;
    }

    /** Translates the following drawing in GUI coordinates (push, translate, then pop). */
    public PainterJS translate(float x, float y) {
        guiGraphics.pose().translate(x, y, 0);
        return this;
    }

    /** Clips: later drawing stays inside the rectangle. */
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
