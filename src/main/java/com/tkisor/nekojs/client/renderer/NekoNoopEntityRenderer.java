// 26.x implementation, so this file must not contain version guards. The 1.21.1 implementation
// is the same-named file under versions/1.21.1/src; changing this file's behavior means
// updating that one too.
package com.tkisor.nekojs.client.renderer;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;

public class NekoNoopEntityRenderer extends EntityRenderer<Entity, EntityRenderState> {
    public NekoNoopEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }
}
