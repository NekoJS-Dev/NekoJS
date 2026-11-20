package com.tkisor.nekojs.bindings.event;

import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

final class NeoForgeDamagePostRouting {
    private NeoForgeDamagePostRouting() {}

    static boolean capture(LivingDamageEvent.Post event) {
        return true;
    }

    static boolean versioned(LivingDamageEvent.Post event) {
        return true;
    }

    static boolean stable(LivingDamageEvent.Post event) {
        return !event.getEntity().level().isClientSide();
    }

    static float amount(LivingDamageEvent.Post event) {
        return event.getHealthDamage();
    }
}
