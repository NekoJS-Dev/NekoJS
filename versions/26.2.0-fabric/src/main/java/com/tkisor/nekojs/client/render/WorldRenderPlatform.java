package com.tkisor.nekojs.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;

import java.util.function.Consumer;

/** Minecraft 26.2 staged line submission, isolated from the vanilla prepared frame. */
final class WorldRenderPlatform {
    private WorldRenderPlatform() {}

    static void draw(Consumer<VertexConsumer> vertices) {
        var lines = RenderTypes.linesTranslucent();
        // The vanilla shared stage can still belong to an in-flight feature frame here.
        try (var stage = new StagedVertexBuffer(() -> "NekoJS world lines", RenderType.TRANSIENT_BUFFER_SIZE)) {
            var draw = stage.appendDraw(lines.format(), lines.primitiveTopology());
            vertices.accept(stage.getVertexBuilder(draw));
            stage.upload();
            var modelView = RenderSystem.getModelViewStack();
            modelView.pushMatrix();
            try {
                // AfterLevel runs after vanilla has popped its world transform. Set, do not multiply,
                // so earlier callbacks that still have camera rotation do not apply it twice.
                modelView.set(Minecraft.getInstance().gameRenderer.gameRenderState()
                        .levelRenderState.cameraRenderState.viewRotationMatrix);
                var prepared = lines.prepare();
                var info = stage.getExecuteInfo(draw);
                if (info != null) {
                    prepared.drawFromBuffer(info);
                }
            } finally {
                modelView.popMatrix();
            }
        }
    }
}
