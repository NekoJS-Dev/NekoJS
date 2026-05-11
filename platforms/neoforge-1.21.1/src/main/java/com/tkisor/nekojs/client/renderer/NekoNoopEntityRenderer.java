package com.tkisor.nekojs.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

public class NekoNoopEntityRenderer extends EntityRenderer<Entity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("nekojs", "textures/entity/noop.png");

    public NekoNoopEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(Entity entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
    }

    @Override
    public ResourceLocation getTextureLocation(Entity entity) {
        return TEXTURE;
    }
}
