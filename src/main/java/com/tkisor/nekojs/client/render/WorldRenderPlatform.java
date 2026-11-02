package com.tkisor.nekojs.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;

import java.util.function.Consumer;

/** Minecraft 26.1 immediate line submission; 26.2 nodes supply a staged implementation. */
final class WorldRenderPlatform {
    private WorldRenderPlatform() {}

    static void draw(Consumer<VertexConsumer> vertices) {
        var lines = RenderTypes.linesTranslucent();
        var source = Minecraft.getInstance().renderBuffers().bufferSource();
        try {
            vertices.accept(source.getBuffer(lines));
        } finally {
            source.endBatch(lines);
        }
    }
}
