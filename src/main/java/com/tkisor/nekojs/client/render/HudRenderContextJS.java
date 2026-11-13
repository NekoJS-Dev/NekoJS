// 26.x implementation. This file should not contain any version guards. The 1.21.1 implementation is in the file with the same name under versions/1.21.1/src.
// When changing the behavior of this file, synchronize the 1.21.1 file.
package com.tkisor.nekojs.client.render;

import com.tkisor.nekojs.api.ui.UiColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Provides the callback context for {@code ClientEvents.hudRender} (the 26.x
 * {@link GuiGraphicsExtractor} render state model). Carries partial ticks and the scaled screen dimensions,
 * and offers common drawing helpers (ported from Katton's HUD draw helpers, named to align with
 * {@code PainterJS}; {@code drawText}/{@code fillRect}/{@code drawTexture} are aliases for task specifications).
 *
 * <p>Color parameters are in ARGB format (e.g., {@code 0x80FF0000} = translucent red) and are received as
 * {@link Number}, read as uint32 bits ({@link UiColor#argbBits}). Script literals like
 * {@code 0xFF......} are unsigned numbers ≥ 2³¹; when directly passed to a Java {@code int} parameter,
 * they are saturated by the engine to {@code 0x7FFFFFFF} (translucent white; defect D6, governed
 * similarly to {@code PainterJS}).
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

    /** Returns the partial interpolation (0~1) of the current render frame, used for smooth animation. */
    public float getPartialTick() {
        return partialTick;
    }

    /** Returns the screen width (after GUI scaling). */
    public int getWidth() {
        return graphics.guiWidth();
    }

    /** Returns the screen height (after GUI scaling). */
    public int getHeight() {
        return graphics.guiHeight();
    }

    /** Returns the raw graphics object (26.x {@link GuiGraphicsExtractor}), allowing scripts to call unwrapped functionality directly. */
    public GuiGraphicsExtractor graphics() {
        return graphics;
    }

    /** Sets the default color (ARGB; {@link Number} values are read as uint32) for drawing methods that omit the color parameter. */
    public HudRenderContextJS color(Number color) {
        this.currentColor = UiColor.argbBits(color);
        return this;
    }

    /** Resets the default color to opaque white. */
    public HudRenderContextJS resetColor() {
        this.currentColor = 0xFFFFFFFF;
        return this;
    }

    /** Draws text, left-aligned. */
    public HudRenderContextJS text(String text, int x, int y) {
        graphics.text(font, text, x, y, currentColor);
        return this;
    }

    public HudRenderContextJS text(String text, int x, int y, Number color) {
        graphics.text(font, text, x, y, UiColor.argbBits(color));
        return this;
    }

    /** Provides an alias for {@link #text(String, int, int, Number)}. */
    public HudRenderContextJS drawText(String text, int x, int y, Number color) {
        return text(text, x, y, color);
    }

    /** Draws text, horizontally centered. */
    public HudRenderContextJS centerText(String text, int x, int y) {
        graphics.centeredText(font, text, x, y, currentColor);
        return this;
    }

    public HudRenderContextJS centerText(String text, int x, int y, Number color) {
        graphics.centeredText(font, text, x, y, UiColor.argbBits(color));
        return this;
    }

    /** Draws a solid rectangle using width and height. */
    public HudRenderContextJS rect(int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, currentColor);
        return this;
    }

    public HudRenderContextJS rect(int x, int y, int width, int height, Number color) {
        graphics.fill(x, y, x + width, y + height, UiColor.argbBits(color));
        return this;
    }

    /** Provides an alias for {@link #rect(int, int, int, int, Number)}. */
    public HudRenderContextJS fillRect(int x, int y, int width, int height, Number color) {
        return rect(x, y, width, height, color);
    }

    /** Draws a 1px rectangle outline. */
    public HudRenderContextJS outline(int x, int y, int width, int height) {
        graphics.outline(x, y, width, height, currentColor);
        return this;
    }

    public HudRenderContextJS outline(int x, int y, int width, int height, Number color) {
        graphics.outline(x, y, width, height, UiColor.argbBits(color));
        return this;
    }

    /** Draws a rectangle with a vertical color gradient. */
    public HudRenderContextJS gradient(int x, int y, int width, int height, Number colorTop, Number colorBottom) {
        graphics.fillGradient(x, y, x + width, y + height,
                UiColor.argbBits(colorTop), UiColor.argbBits(colorBottom));
        return this;
    }

    /**
     * Draws a texture (full texture ID, e.g., {@code 'minecraft:textures/gui/icons.png'}), where u/v are offsets within the texture.
     */
    public HudRenderContextJS texture(String textureId, int x, int y, int width, int height, int u, int v) {
        return texture(textureId, x, y, width, height, u, v, 256, 256);
    }

    /** Draws the entire texture using the legacy 256-pixel source contract. */
    public HudRenderContextJS texture(String textureId, int x, int y, int width, int height) {
        return texture(textureId, x, y, width, height, 0, 0);
    }

    public HudRenderContextJS texture(String textureId, int x, int y, int width, int height,
            int u, int v, int textureWidth, int textureHeight) {
        if (textureWidth <= 0 || textureHeight <= 0) {
            throw new IllegalArgumentException("[NEKO-4026] Texture dimensions must be positive");
        }
        graphics.blit(Identifier.parse(textureId), x, y, x + width, y + height,
                (float) u / textureWidth, ((float) u + width) / textureWidth,
                (float) v / textureHeight, ((float) v + height) / textureHeight);
        return this;
    }

    /** Draws the entire texture scaled to the requested rectangle. */
    public HudRenderContextJS textureFull(String textureId, int x, int y, int width, int height) {
        graphics.blit(Identifier.parse(textureId), x, y, x + width, y + height, 0F, 1F, 0F, 1F);
        return this;
    }

    /** Provides an alias for {@link #texture(String, int, int, int, int, int, int)}. */
    public HudRenderContextJS drawTexture(String textureId, int x, int y, int width, int height, int u, int v) {
        return texture(textureId, x, y, width, height, u, v);
    }

    /** Draws an item icon (e.g., {@code Item.of('minecraft:diamond')}). */
    public HudRenderContextJS item(ItemStack stack, int x, int y) {
        graphics.item(stack, x, y);
        return this;
    }

    /** Enables scissoring: limits subsequent drawing to the specified rectangular area. */
    public HudRenderContextJS scissor(int x, int y, int width, int height) {
        graphics.enableScissor(x, y, x + width, y + height);
        return this;
    }

    public HudRenderContextJS resetScissor() {
        graphics.disableScissor();
        return this;
    }
}
