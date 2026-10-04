//? if neoforge && >=26 {
package com.tkisor.nekojs.client.ui;

import com.tkisor.nekojs.api.ui.UiDiagnostic;
import com.tkisor.nekojs.api.ui.UiErrorCodes;
import com.tkisor.nekojs.api.ui.VisualSpec;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Immutable texture geometry prepared before paint; painting performs no file reads or decoding. */
public record UiTextureBlitPlan(Identifier texture, int x, int y, int width, int height,
                                float sourceX, float sourceY, int sourceWidth, int sourceHeight,
                                int textureWidth, int textureHeight, int argb) {
    public static Result prepare(MinecraftUiResourceResolver.Texture texture, VisualSpec.CropRect crop,
                                 VisualSpec.ImageFit fit, double opacity, int x, int y, int width, int height,
                                 UiDiagnostic.Location location) {
        UiDiagnostic resourceFailure = texture.diagnostic(location);
        if (resourceFailure != null) return new Result(null, resourceFailure);
        if (width <= 0 || height <= 0) return new Result(null, null);
        if (!Double.isFinite(opacity) || opacity < 0 || opacity > 1) {
            return invalid(texture, location, UiErrorCodes.INVALID_VISUAL_VALUE, "UI image opacity must be in [0, 1]");
        }
        int sourceX = crop == null ? 0 : crop.x();
        int sourceY = crop == null ? 0 : crop.y();
        int sourceWidth = crop == null ? texture.width() : crop.width();
        int sourceHeight = crop == null ? texture.height() : crop.height();
        if ((long) sourceX + sourceWidth > texture.width() || (long) sourceY + sourceHeight > texture.height()) {
            return invalid(texture, location, UiErrorCodes.INVALID_RESOURCE_SIZE, "UI image crop exceeds texture dimensions");
        }
        VisualSpec.ImageFit mode = fit == null ? VisualSpec.ImageFit.CONTAIN : fit;
        int drawWidth = width;
        int drawHeight = height;
        float drawSourceX = sourceX;
        float drawSourceY = sourceY;
        if (mode == VisualSpec.ImageFit.CONTAIN) {
            double scale = Math.min((double) width / sourceWidth, (double) height / sourceHeight);
            drawWidth = Math.max(1, Math.min(width, (int) Math.round(sourceWidth * scale)));
            drawHeight = Math.max(1, Math.min(height, (int) Math.round(sourceHeight * scale)));
        } else if (mode == VisualSpec.ImageFit.COVER) {
            double scale = Math.max((double) width / sourceWidth, (double) height / sourceHeight);
            int visibleWidth = Math.max(1, Math.min(sourceWidth, (int) Math.round(width / scale)));
            int visibleHeight = Math.max(1, Math.min(sourceHeight, (int) Math.round(height / scale)));
            drawSourceX += (sourceWidth - visibleWidth) / 2.0f;
            drawSourceY += (sourceHeight - visibleHeight) / 2.0f;
            sourceWidth = visibleWidth;
            sourceHeight = visibleHeight;
        }
        int color = ((int) Math.round(opacity * 255) << 24) | 0x00FFFFFF;
        return new Result(new UiTextureBlitPlan(texture.slot(), x + (width - drawWidth) / 2,
                y + (height - drawHeight) / 2, drawWidth, drawHeight, drawSourceX, drawSourceY,
                sourceWidth, sourceHeight, texture.width(), texture.height(), color), null);
    }

    public void paint(GuiGraphicsExtractor graphics) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, sourceX, sourceY, width, height,
                sourceWidth, sourceHeight, textureWidth, textureHeight, argb);
    }

    private static Result invalid(MinecraftUiResourceResolver.Texture texture, UiDiagnostic.Location location,
                                  String code, String message) {
        return new Result(null, new UiDiagnostic(code, message, location.rootId(), location.nodeType(),
                location.nodeKey(), texture.status().id(), location.generation()));
    }

    public record Result(UiTextureBlitPlan plan, UiDiagnostic diagnostic) { }
}
//?}
