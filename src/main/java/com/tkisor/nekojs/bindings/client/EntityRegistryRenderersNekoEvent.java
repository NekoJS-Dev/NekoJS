package com.tkisor.nekojs.bindings.client;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public class EntityRegistryRenderersNekoEvent implements ClientNekoEvent {
    private final EntityRenderersEvent.RegisterRenderers event;

    public EntityRegistryRenderersNekoEvent(EntityRenderersEvent.RegisterRenderers event) {
        this.event = event;
    }

    public EntityRenderersEvent.RegisterRenderers getRawEvent() {
        return event;
    }

    public void registerEntityRenderer(EntityType<?> type, EntityRendererProvider renderer) {
        event.registerEntityRenderer(type, renderer);
    }

    public void registerBlockEntityRenderer(BlockEntityType<?> type, BlockEntityRendererProvider renderer) {
        event.registerBlockEntityRenderer(type, renderer);
    }
}
