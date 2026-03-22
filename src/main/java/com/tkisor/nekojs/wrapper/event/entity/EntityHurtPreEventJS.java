package com.tkisor.nekojs.wrapper.event.entity;

import com.tkisor.nekojs.wrapper.entity.EntityWrapper;
import com.tkisor.nekojs.wrapper.entity.LivingEntityWrapper;
import lombok.Getter;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

public class EntityHurtPreEventJS {
    private final LivingDamageEvent.Pre rawEvent;

    @Getter private final LivingEntityWrapper entity;
    @Getter private final EntityWrapper attacker;

    public EntityHurtPreEventJS(LivingDamageEvent.Pre rawEvent) {
        this.rawEvent = rawEvent;
        this.entity = new LivingEntityWrapper(rawEvent.getEntity());
        Entity trueAttacker = rawEvent.getSource().getEntity();
        this.attacker = trueAttacker != null ? EntityWrapper.of(trueAttacker) : null;
    }

    public String getEntityId() { return this.entity.getId(); }
    public String getDamageType() { return rawEvent.getSource().type().msgId(); }

    public float getDamage() { return rawEvent.getNewDamage(); }
    public void setDamage(float amount) { rawEvent.setNewDamage(amount); }

}