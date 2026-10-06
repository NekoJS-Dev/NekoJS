package com.tkisor.nekojs.client.renderer;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

/** Draws a textured, animated humanoid through the vanilla living-entity pipeline. */
public final class NekoHumanoidEntityRenderer extends LivingEntityRenderer<LivingEntity, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
    private final Identifier texture;

    public NekoHumanoidEntityRenderer(EntityRendererProvider.Context context, Identifier texture, float shadowRadius) {
        super(context, new HumanoidModel<>(LayerDefinition.create(
                HumanoidModel.createMesh(CubeDeformation.NONE, 0F), 64, 64).bakeRoot()), shadowRadius);
        this.texture = texture;
    }

    @Override
    public HumanoidRenderState createRenderState() {
        return new HumanoidRenderState();
    }

    @Override
    public void extractRenderState(LivingEntity entity, HumanoidRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        HumanoidMobRenderer.extractHumanoidRenderState(entity, state, partialTick, itemModelResolver);
    }

    @Override
    public Identifier getTextureLocation(HumanoidRenderState state) {
        return texture;
    }
}
