package com.tkisor.nekojs.wrapper.event.entity;

import com.tkisor.nekojs.bindings.event.NekoEvent;
import com.tkisor.nekojs.wrapper.entity.EntityWrapper;
import lombok.Getter;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

public class EntityDeathEventJS implements NekoEvent {
    private final LivingDeathEvent rawEvent;

    @Getter
    private final EntityWrapper entity;

    @Getter
    private final EntityWrapper attacker;

    @Getter
    private boolean cancelled;

    public EntityDeathEventJS(LivingDeathEvent rawEvent) {
        this.rawEvent = rawEvent;
        this.entity = new EntityWrapper(rawEvent.getEntity());

        Entity trueAttacker = rawEvent.getSource().getEntity();
        this.attacker = trueAttacker != null ? new EntityWrapper(trueAttacker) : null;
    }

    public String getEntityId() {
        return this.entity.getId();
    }

    public String getDamageType() {
        return rawEvent.getSource().type().msgId();
    }

    public void cancel() {
        rawEvent.setCanceled(true);
        this.cancelled = true;
    }
}