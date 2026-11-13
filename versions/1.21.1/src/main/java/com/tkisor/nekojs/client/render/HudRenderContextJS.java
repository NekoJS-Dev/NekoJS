// 1.21.1 implementation. It pairs with the same-named 26.x file under src/ in the version
// tree: this file is that file evaluated for this node. Keep them in sync when 26.x changes.
package com.tkisor.nekojs.client.render;

import com.tkisor.nekojs.api.ui.UiColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Context of the {@code ClientEvents.hudRender} callback (26.x's {@link GuiGraphics} render
 * state model). It carries the partial tick and the scaled screen size, and offers the common
 * draw helpers (ported from Katton's HUD draw helpers, named to match {@code PainterJS};
 * {@code drawText}/{@code fillRect}/{@code drawTexture} are aliases).
 *
 * <p>Color parameters are ARGB (for example {@code 0x80FF0000} is half-transparent red). They
 * arrive as {@link Number} and are read as uint32 bits ({@link UiColor#argbBits}): a script
 * literal {@code 0xFF......} is an unsigned value of at least 2^31, and passing it straight to
 * a Java {@code int} parameter makes the engine saturate it to {@code 0x7FFFFFFF}
 * (half-transparent white). {@code PainterJS} handles it the same way.
 */
public class HudRenderContextJS {
    private final GuiGraphics graphics;
    private final Font font;
    private final float partialTick;
    private int currentColor = 0xFFFFFFFF;

    public HudRenderContextJS(GuiGraphics graphics, float partialTick) {
        this.graphics = graphics;
        this.font = Minecraft.getInstance().font;
        this.partialTick = partialTick;
    }

    /** Fraction of the current render frame (0 to 1) applied to smooth animation. */
    public float getPartialTick() {
        return partialTick;
    }

    /** Screen width after GUI scaling. */
    public int getWidth() {
        return Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }

    /** Screen height after GUI scaling. */
    public int getHeight() {
        return Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }

    /** Raw graphics object (26.x {@link GuiGraphics}) for calling unwrapped methods directly. */
    public GuiGraphics graphics() {
        return graphics;
    }

    /**
     * Sets the default color (ARGB, {@link Number} read as uint32 bits) used by draw methods that
     * omit the color argument.
     */
    public HudRenderContextJS color(Number color) {
        this.currentColor = UiColor.argbBits(color);
        return this;
    }

    /** Resets the default color to opaque white. */
    public HudRenderContextJS resetColor() {
        this.currentColor = 0xFFFFFFFF;
        return this;
    }

    /** Draws text, left aligned. */
    public HudRenderContextJS text(String text, int x, int y) {
        graphics.drawString(font, text, x, y, currentColor);
        return this;
    }

    public HudRenderContextJS text(String text, int x, int y, Number color) {
        graphics.drawString(font, text, x, y, UiColor.argbBits(color));
        return this;
    }

    /** Alias for {@link #text(String, int, int, Number)}. */
    public HudRenderContextJS drawText(String text, int x, int y, Number color) {
        return text(text, x, y, color);
    }

    /** Draws text, centered horizontally. */
    public HudRenderContextJS centerText(String text, int x, int y) {
        graphics.drawCenteredString(font, text, x, y, currentColor);
        return this;
    }

    public HudRenderContextJS centerText(String text, int x, int y, Number color) {
        graphics.drawCenteredString(font, text, x, y, UiColor.argbBits(color));
        return this;
    }

    /** Filled rectangle, sized by width and height. */
    public HudRenderContextJS rect(int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, currentColor);
        return this;
    }

    public HudRenderContextJS rect(int x, int y, int width, int height, Number color) {
        graphics.fill(x, y, x + width, y + height, UiColor.argbBits(color));
        return this;
    }

    /** Alias for {@link #rect(int, int, int, int, Number)}. */
    public HudRenderContextJS fillRect(int x, int y, int width, int height, Number color) {
        return rect(x, y, width, height, color);
    }

    /** 1px rectangle outline. */
    public HudRenderContextJS outline(int x, int y, int width, int height) {
        return outline(x, y, width, height, currentColor);
    }

    public HudRenderContextJS outline(int x, int y, int width, int height, Number color) {
        int argb = UiColor.argbBits(color);
        graphics.hLine(x, x + width - 1, y, argb);
        graphics.hLine(x, x + width - 1, y + height - 1, argb);
        graphics.vLine(x, y, y + height - 1, argb);
        graphics.vLine(x + width - 1, y, y + height - 1, argb);
        return this;
    }

    /** Vertical gradient rectangle. */
    public HudRenderContextJS gradient(int x, int y, int width, int height, Number colorTop, Number colorBottom) {
        graphics.fillGradient(x, y, x + width, y + height,
                UiColor.argbBits(colorTop), UiColor.argbBits(colorBottom));
        return this;
    }

    /**
     * Draws a texture. The id is the full texture id, such as
     * {@code 'minecraft:textures/gui/icons.png'}; u/v are offsets inside the texture.
     */
    public HudRenderContextJS texture(String textureId, int x, int y, int width, int height, int u, int v) {
        return texture(textureId, x, y, width, height, u, v, 256, 256);
    }

    public HudRenderContextJS texture(String textureId, int x, int y, int width, int height,
            int u, int v, int textureWidth, int textureHeight) {
        if (textureWidth <= 0 || textureHeight <= 0) {
            throw new IllegalArgumentException("[NEKO-4026] Texture dimensions must be positive");
        }
        graphics.blit(ResourceLocation.parse(textureId), x, y, (float) u, (float) v,
                width, height, textureWidth, textureHeight);
        return this;
    }

    /** Draws the entire texture scaled to the requested rectangle. */
    public HudRenderContextJS textureFull(String textureId, int x, int y, int width, int height) {
        if (width == 0 || height == 0) return this;
        graphics.blit(ResourceLocation.parse(textureId), x, y, 0F, 0F, width, height, width, height);
        return this;
    }

    /** Draws the whole texture, with u and v at 0. */
    public HudRenderContextJS texture(String textureId, int x, int y, int width, int height) {
        graphics.blit(ResourceLocation.parse(textureId), x, y, 0, 0, width, height);
        return this;
    }

    /** Alias for {@link #texture(String, int, int, int, int, int, int)}. */
    public HudRenderContextJS drawTexture(String textureId, int x, int y, int width, int height, int u, int v) {
        return texture(textureId, x, y, width, height, u, v);
    }

    /** Draws an item icon, for example {@code Item.of('minecraft:diamond')}. */
    public HudRenderContextJS item(ItemStack stack, int x, int y) {
        graphics.renderItem(stack, x, y);
        return this;
    }

    /** Clips later drawing to the given rectangle. */
    public HudRenderContextJS scissor(int x, int y, int width, int height) {
        graphics.enableScissor(x, y, x + width, y + height);
        return this;
    }

    public HudRenderContextJS resetScissor() {
        graphics.disableScissor();
        return this;
    }
}
