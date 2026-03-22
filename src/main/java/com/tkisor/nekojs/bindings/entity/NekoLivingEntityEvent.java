package com.tkisor.nekojs.bindings.entity;

import com.tkisor.nekojs.wrapper.entity.LivingEntityWrapper;

public interface NekoLivingEntityEvent extends NekoEntityEvent {
    @Override
    LivingEntityWrapper getEntity();
}
