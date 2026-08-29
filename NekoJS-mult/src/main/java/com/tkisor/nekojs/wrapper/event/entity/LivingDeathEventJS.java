package com.tkisor.nekojs.wrapper.event.entity;

import lombok.Getter;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * 生物死亡事件的中立 payload（fabric 桥首用；成员 {@code event.entity} /
 * {@code event.source}，与 NeoForge 原生 LivingDeathEvent 的 getEntity/getSource 同形）。
 */
public class LivingDeathEventJS {

    @Getter
    private final LivingEntity entity;

    @Getter
    private final DamageSource source;

    public LivingDeathEventJS(LivingEntity entity, DamageSource source) {
        this.entity = entity;
        this.source = source;
    }
}
