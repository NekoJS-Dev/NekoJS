// 1.21.1 实现，与版本树 src/ 下的同名 26.x 文件成对。内容等于那份文件在本节点求值后的形态
//（可用 tools/extract_evaluated.py 重新提取核对）；26.x 侧行为变更时须同步本文件。
package com.tkisor.nekojs.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/**
 * {@code ClientEvents.worldRender} 回调上下文：相机位置 + partialTick，以及
 * 世界空间 3D 线段绘制助手（移植自 Katton 的 drawLine3D：真实 GPU 网格渲染，
 * VertexConsumer + RenderType，per-vertex 法线取线段方向）。
 *
 * <p>坐标为世界坐标（内部自动减去相机位置）；颜色为 ARGB int。
 *
 * <p>26.1 与 26.2 共用本类，但 26.2 移除了 {@code MultiBufferSource}（渲染管线
 * 改为 GraphicsResourceAllocator/StagedVertexBuffer 模型），无法静态引用
 * buffer source 类型——取 buffer source 的链路（{@code Minecraft#renderBuffers()}
 * → {@code RenderBuffers#bufferSource()}）走反射：26.1 可用，26.2 找不到方法时
 * 优雅降级（告警一次、line/box 静默跳过），HUD 渲染不受影响。
 */
public class WorldRenderContextJS {
    private final Vec3 cameraPos;
    private final float partialTick;

    /** 反射解析的 buffer source 实例（26.1 客户端生命周期内不变）；26.2 为 {@code null}。 */

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
     * @param lineWidth 线宽（像素，最小 1）
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

    /** 绘制一条 3D 线段（默认线宽 1）。 */
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

    /**
     * 解析 immediate buffer source：{@code Minecraft#renderBuffers()} →
     * {@code RenderBuffers#bufferSource()}（26.1 存在；26.2 均已移除 → null 降级）。
     */
}
