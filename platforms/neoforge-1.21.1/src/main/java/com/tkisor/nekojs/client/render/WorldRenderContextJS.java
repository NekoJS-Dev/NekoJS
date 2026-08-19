package com.tkisor.nekojs.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;

/**
 * {@code ClientEvents.worldRender} 回调上下文（1.21.1）：相机位置 + partialTick，
 * 以及世界空间 3D 线段绘制助手（移植自 Katton 的 drawLine3D：真实 GPU 网格渲染，
 * VertexConsumer + RenderType，per-vertex 法线取线段方向）。
 *
 * <p>坐标为世界坐标（内部自动减去相机位置）；颜色为 ARGB int。
 * 1.21.1 的 {@link RenderType} 无 per-vertex 线宽，{@code lineWidth} 参数仅接受语义。
 */
public class WorldRenderContextJS {
    private final Vec3 cameraPos;
    private final float partialTick;

    public WorldRenderContextJS(Vec3 cameraPos, float partialTick) {
        this.cameraPos = cameraPos;
        this.partialTick = partialTick;
    }

    /** 当前渲染帧的部分插值（0~1）。 */
    public float getPartialTick() {
        return partialTick;
    }

    /** 相机世界位置（线段绘制的原点参考）。 */
    public Vec3 getCameraPos() {
        return cameraPos;
    }

    /**
     * 绘制一条 3D 线段（世界坐标 → 世界空间 GPU 渲染）。
     *
     * @param argbColor ARGB 颜色（如 {@code 0xFF00FF00}）
     * @param lineWidth 1.21.1 无 per-vertex 线宽，参数被忽略（保持跨版本调用形状一致）
     */
    public WorldRenderContextJS line(
            double x1, double y1, double z1,
            double x2, double y2, double z2,
            int argbColor,
            float lineWidth
    ) {
        int a = (argbColor >>> 24) & 0xFF;
        int r = (argbColor >>> 16) & 0xFF;
        int g = (argbColor >>> 8) & 0xFF;
        int b = argbColor & 0xFF;

        // 线段方向作为 per-vertex 法线（RenderType.LINES 的屏幕空间扩展依据）
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

    /** 绘制一条 3D 线段（1.21.1 固定线宽）。 */
    public WorldRenderContextJS line(
            double x1, double y1, double z1,
            double x2, double y2, double z2,
            int argbColor
    ) {
        return line(x1, y1, z1, x2, y2, z2, argbColor, 1f);
    }

    /** 绘制世界空间长方体边框（12 条棱）。 */
    public WorldRenderContextJS box(
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            int argbColor
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
}
