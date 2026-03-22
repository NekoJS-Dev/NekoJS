package com.tkisor.nekojs.wrapper.event.entity;

import com.tkisor.nekojs.api.event.NekoCancellableEvent;
import com.tkisor.nekojs.wrapper.entity.EntityWrapper;
import lombok.Getter;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

public class EntityDeathEventJS implements NekoCancellableEvent {
    private final LivingDeathEvent rawEvent;

    @Getter
    private final EntityWrapper entity;

    @Getter
    private final EntityWrapper attacker;

    public EntityDeathEventJS(LivingDeathEvent rawEvent) {
        this.rawEvent = rawEvent;
        this.entity = EntityWrapper.of(rawEvent.getEntity());

        Entity trueAttacker = rawEvent.getSource().getEntity();
        this.attacker = trueAttacker != null ? EntityWrapper.of(trueAttacker) : null;
    }

    public String getEntityId() {
        return this.entity.getId();
    }

    public String getDamageType() {
        return rawEvent.getSource().type().msgId();
    }

}