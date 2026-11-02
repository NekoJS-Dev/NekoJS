package com.tkisor.nekojs.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tkisor.nekojs.api.ui.UiColor;
import net.minecraft.world.phys.Vec3;

/**
 * World-render callback context with camera-relative line and box helpers.
 * Coordinates are world coordinates and colors retain their signed or unsigned ARGB bits.
 * The node facade owns submission to the current Minecraft rendering pipeline.
 */
public class WorldRenderContextJS {
    private final Vec3 cameraPos;
    private final float partialTick;

    public WorldRenderContextJS(Vec3 cameraPos, float partialTick) {
        this.cameraPos = cameraPos;
        this.partialTick = partialTick;
    }

    /** Current frame interpolation value. */
    public float getPartialTick() {
        return partialTick;
    }

    /** World-space camera origin used for drawing. */
    public Vec3 getCameraPos() {
        return cameraPos;
    }

    /** Draws a world-space line with a per-vertex pixel width of at least one. */
    public WorldRenderContextJS line(
            double x1, double y1, double z1,
            double x2, double y2, double z2,
            Number argbColor,
            float lineWidth
    ) {
        int argb = UiColor.argbBits(argbColor);
        float width = Math.max(1f, lineWidth);
        WorldRenderPlatform.draw(consumer -> emitLine(consumer, x1, y1, z1, x2, y2, z2, argb, width));
        return this;
    }

    /** Draws a world-space line with a one-pixel width. */
    public WorldRenderContextJS line(
            double x1, double y1, double z1,
            double x2, double y2, double z2,
            Number argbColor
    ) {
        return line(x1, y1, z1, x2, y2, z2, argbColor, 1f);
    }

    /** Draws all twelve edges of a world-space box in one submission. */
    public WorldRenderContextJS box(
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            Number argbColor
    ) {
        int argb = UiColor.argbBits(argbColor);
        WorldRenderPlatform.draw(consumer -> {
            emitLine(consumer, minX, minY, minZ, maxX, minY, minZ, argb, 1f);
            emitLine(consumer, maxX, minY, minZ, maxX, minY, maxZ, argb, 1f);
            emitLine(consumer, maxX, minY, maxZ, minX, minY, maxZ, argb, 1f);
            emitLine(consumer, minX, minY, maxZ, minX, minY, minZ, argb, 1f);
            emitLine(consumer, minX, maxY, minZ, maxX, maxY, minZ, argb, 1f);
            emitLine(consumer, maxX, maxY, minZ, maxX, maxY, maxZ, argb, 1f);
            emitLine(consumer, maxX, maxY, maxZ, minX, maxY, maxZ, argb, 1f);
            emitLine(consumer, minX, maxY, maxZ, minX, maxY, minZ, argb, 1f);
            emitLine(consumer, minX, minY, minZ, minX, maxY, minZ, argb, 1f);
            emitLine(consumer, maxX, minY, minZ, maxX, maxY, minZ, argb, 1f);
            emitLine(consumer, maxX, minY, maxZ, maxX, maxY, maxZ, argb, 1f);
            emitLine(consumer, minX, minY, maxZ, minX, maxY, maxZ, argb, 1f);
        });
        return this;
    }

    private void emitLine(VertexConsumer consumer,
                          double x1, double y1, double z1,
                          double x2, double y2, double z2,
                          int argb, float width) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double inverse = length < 1e-6 ? 0 : 1 / length;
        float nx = (float) (dx * inverse);
        float ny = (float) (dy * inverse);
        float nz = (float) (dz * inverse);
        int alpha = (argb >>> 24) & 0xFF;
        int red = (argb >>> 16) & 0xFF;
        int green = (argb >>> 8) & 0xFF;
        int blue = argb & 0xFF;

        // Subtract in double precision before narrowing, including near the world border.
        consumer.addVertex((float) (x1 - cameraPos.x), (float) (y1 - cameraPos.y), (float) (z1 - cameraPos.z))
                .setColor(red, green, blue, alpha)
                .setNormal(nx, ny, nz)
                .setLineWidth(width);
        consumer.addVertex((float) (x2 - cameraPos.x), (float) (y2 - cameraPos.y), (float) (z2 - cameraPos.z))
                .setColor(red, green, blue, alpha)
                .setNormal(nx, ny, nz)
                .setLineWidth(width);
    }
}
