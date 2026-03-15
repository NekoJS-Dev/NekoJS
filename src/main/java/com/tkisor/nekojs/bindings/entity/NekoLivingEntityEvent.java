package com.tkisor.nekojs.bindings.entity;

import net.minecraft.world.entity.LivingEntity;

public interface NekoLivingEntityEvent extends NekoEntityEvent {
    @Override
    LivingEntity getEntity();
}
