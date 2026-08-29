package com.tkisor.nekojs.client.renderer;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
//? if >=26 {
import net.minecraft.client.renderer.entity.state.EntityRenderState;
//?}
import net.minecraft.world.entity.Entity;
//? if <26 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.Identifier;
*///?}

//? if >=26 {
public class NekoNoopEntityRenderer extends EntityRenderer<Entity, EntityRenderState> {
//?} else {
/*public class NekoNoopEntityRenderer extends EntityRenderer<Entity> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("nekojs", "textures/entity/noop.png");
*///?}
    public NekoNoopEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
//? if >=26 {
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
//?} else {
/*    public void render(Entity entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
    }

    @Override
    public Identifier getTextureLocation(Entity entity) {
        return TEXTURE;
*///?}
    }
}
