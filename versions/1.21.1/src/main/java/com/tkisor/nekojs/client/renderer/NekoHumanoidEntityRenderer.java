package com.tkisor.nekojs.client.renderer;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/** Draws a textured humanoid through the 1.21.1 living-entity pipeline. */
public final class NekoHumanoidEntityRenderer extends LivingEntityRenderer<LivingEntity, HumanoidModel<LivingEntity>> {
    private final ResourceLocation texture;

    public NekoHumanoidEntityRenderer(EntityRendererProvider.Context context, ResourceLocation texture, float shadowRadius) {
        super(context, new HumanoidModel<>(LayerDefinition.create(
                HumanoidModel.createMesh(CubeDeformation.NONE, 0F), 64, 64).bakeRoot()), shadowRadius);
        this.texture = texture;
    }

    @Override
    public ResourceLocation getTextureLocation(LivingEntity entity) {
        return texture;
    }
}
