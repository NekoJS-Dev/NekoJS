// 1.21.1 implementation, paired with the same-named 26.x file under the version tree's src/. The content
// is what that file becomes once evaluated for this node. Keep this file in sync when the 26.x behavior
// changes.
package com.tkisor.nekojs.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tkisor.nekojs.api.ui.UiColor;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/**
 * Context for the {@code ClientEvents.worldRender} callback: the camera position and partialTick,
 * plus helpers that draw 3D line segments in world space. Ported from Katton's drawLine3D, which
 * renders a real GPU mesh through VertexConsumer and RenderType and uses the segment direction as
 * the per-vertex normal.
 *
 * <p>Coordinates are world coordinates; the camera position is subtracted internally. Colors are
 * ARGB ints.
 *
 * <p>26.1 and 26.2 share this class, but 26.2 removed {@code MultiBufferSource} (the render
 * pipeline now uses the GraphicsResourceAllocator/StagedVertexBuffer model), so the buffer source
 * type cannot be referenced statically. The chain that gets one ({@code Minecraft#renderBuffers()}
 * then {@code RenderBuffers#bufferSource()}) therefore goes through reflection: it works on 26.1,
 * and on 26.2, where the method is missing, it degrades gracefully (one warning, line and box are
 * silently skipped) while HUD rendering is unaffected.
 */
public class WorldRenderContextJS {
    private final Vec3 cameraPos;
    private final float partialTick;

    /** Buffer source resolved through reflection; constant for the client lifetime on 26.1, {@code null} on 26.2. */

    public WorldRenderContextJS(Vec3 cameraPos, float partialTick) {
        this.cameraPos = cameraPos;
        this.partialTick = partialTick;
    }

    /** Partial interpolation of the current render frame, from 0 to 1. */
    public float getPartialTick() {
        return partialTick;
    }

    /** Camera position in the world; line drawing uses it as the origin. */
    public Vec3 getCameraPos() {
        return cameraPos;
    }

    /**
     * Draws one 3D line segment, from world coordinates to a GPU mesh in world space.
     *
     * @param argbColor ARGB color, for example {@code 0xFF00FF00}. A {@link Number} value is read as
     *        uint32 bits; an unsigned literal of 2³¹ or more that lands in a Java {@code int} parameter
     *        is saturated to {@code 0x7FFFFFFF} by the engine
     * @param lineWidth line width in pixels, at least 1
     */
    public WorldRenderContextJS line(
            double x1, double y1, double z1,
            double x2, double y2, double z2,
            Number argbColor,
            float lineWidth
    ) {
        int argb = UiColor.argbBits(argbColor);
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        // The segment direction becomes the per-vertex normal that RenderType.LINES uses to expand the line
        // in screen space.
        float dx = (float) (x2 - x1);
        float dy = (float) (y2 - y1);
        float dz = (float) (z2 - z1);
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float inv = len < 1e-6f ? 0f : 1f / len;
        float nx = dx * inv;
        float ny = dy * inv;
        float nz = dz * inv;

        PoseStack poseStack = new PoseStack();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        var pose = poseStack.last();

        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());
        consumer.addVertex(pose, (float) x1, (float) y1, (float) z1)
                .setColor(r, g, b, a)
                .setNormal(nx, ny, nz);
        consumer.addVertex(pose, (float) x2, (float) y2, (float) z2)
                .setColor(r, g, b, a)
                .setNormal(nx, ny, nz);
        bufferSource.endBatch(RenderType.lines());
        return this;
    }

    /** Draws one 3D line segment with the default width of 1. */
    public WorldRenderContextJS line(
            double x1, double y1, double z1,
            double x2, double y2, double z2,
            Number argbColor
    ) {
        return line(x1, y1, z1, x2, y2, z2, argbColor, 1f);
    }

    /** Draws the wireframe of a world-space box, as its 12 edges. */
    public WorldRenderContextJS box(
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            Number argbColor
    ) {
        line(minX, minY, minZ, maxX, minY, minZ, argbColor);
        line(maxX, minY, minZ, maxX, minY, maxZ, argbColor);
        line(maxX, minY, maxZ, minX, minY, maxZ, argbColor);
        line(minX, minY, maxZ, minX, minY, minZ, argbColor);
        line(minX, maxY, minZ, maxX, maxY, minZ, argbColor);
        line(maxX, maxY, minZ, maxX, maxY, maxZ, argbColor);
        line(maxX, maxY, maxZ, minX, maxY, maxZ, argbColor);
        line(minX, maxY, maxZ, minX, maxY, minZ, argbColor);
        line(minX, minY, minZ, minX, maxY, minZ, argbColor);
        line(maxX, minY, minZ, maxX, maxY, minZ, argbColor);
        line(maxX, minY, maxZ, maxX, maxY, maxZ, argbColor);
        line(minX, minY, maxZ, minX, maxY, maxZ, argbColor);
        return this;
    }

    /**
     * Resolves the immediate buffer source: {@code Minecraft#renderBuffers()} then
     * {@code RenderBuffers#bufferSource()}. Both exist on 26.1; 26.2 removed them, so the lookup
     * returns {@code null} and callers degrade.
     */
}
