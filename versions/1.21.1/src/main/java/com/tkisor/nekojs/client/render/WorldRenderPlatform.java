package com.tkisor.nekojs.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;

import java.util.function.Consumer;

/** Keeps the shared rendering facade compatible with the 1.21.1 node API. */
final class WorldRenderPlatform {
    private WorldRenderPlatform() {}

    static void draw(Consumer<VertexConsumer> vertices) {
        var lines = RenderType.lines();
        var source = Minecraft.getInstance().renderBuffers().bufferSource();
        try {
            vertices.accept(source.getBuffer(lines));
        } finally {
            source.endBatch(lines);
        }
    }
}
