package com.tkisor.nekojs.bindings.event;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import java.util.function.BooleanSupplier;

public final class NeoForgeDamagePostRouting {
    private static final ThreadLocal<Frame> FRAME = new ThreadLocal<>();
    private static final ThreadLocal<LivingDamageEvent.Post> REPLAY = new ThreadLocal<>();

    private NeoForgeDamagePostRouting() {}

    private static final class Frame {
        private final LivingEntity entity;
        private final DamageSource source;
        private LivingDamageEvent.Post event;

        private Frame(LivingEntity entity, DamageSource source) {
            this.entity = entity;
            this.source = source;
        }
    }

    static boolean capture(LivingDamageEvent.Post event) {
        Frame frame = FRAME.get();
        if (frame != null && frame.entity == event.getEntity() && frame.source == event.getSource()) {
            frame.event = event;
        }
        return true;
    }

    static boolean versioned(LivingDamageEvent.Post event) {
        return REPLAY.get() != event;
    }

    static boolean stable(LivingDamageEvent.Post event) {
        return REPLAY.get() == event;
    }

    public static boolean afterHurt(LivingEntity entity, DamageSource source, BooleanSupplier operation) {
        if (entity.level().isClientSide() || !EntityEvents.DAMAGE_POST.hasListeners()) return operation.getAsBoolean();
        Frame enclosing = FRAME.get();
        Frame frame = new Frame(entity, source);
        FRAME.set(frame);
        boolean result;
        try {
            result = operation.getAsBoolean();
        } finally {
            if (enclosing == null) FRAME.remove();
            else FRAME.set(enclosing);
        }
        if (frame.event != null) {
            LivingDamageEvent.Post previous = REPLAY.get();
            REPLAY.set(frame.event);
            try {
                EntityEvents.DAMAGE_POST.post(frame.event, entity.getType());
            } finally {
                if (previous == null) REPLAY.remove();
                else REPLAY.set(previous);
            }
        }
        return result;
    }
}
