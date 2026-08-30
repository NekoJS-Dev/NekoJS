// 26.x 基准主干（DEVEX-ROADMAP 档 1 整文件拆分）：内联版本守卫已清零，1.21.1 孪生住在
// versions/1.21.1/src 同名文件（构造性变换）；改本文件行为时须同步孪生文件。
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
