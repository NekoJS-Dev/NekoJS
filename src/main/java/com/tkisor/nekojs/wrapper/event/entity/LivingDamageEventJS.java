package com.tkisor.nekojs.wrapper.event.entity;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Loader-neutral payload for the living entity damage event
 * ({@code EntityEvents.damagePre / damagePost}), with members matching the agreed set
 * (entity / source / amount).
 *
 * <p>NeoForge passes its native {@code LivingDamageEvent.Pre/Post} straight through (same
 * members); fabric converts {@code ServerLivingEntityEvents} in
 * {@code FabricEntityEventBindings}.
 *
 * <p><b>Known difference</b>: on NeoForge, Pre lets a script rewrite the damage amount
 * ({@code setNewDamage}), and a cancel (returning {@code true}) maps to
 * {@code setNewDamage(0)}: the damage becomes 0 but the native damage chain still finishes
 * (damagePost still fires with 0 damage). fabric's {@code ALLOW_DAMAGE} can only allow or deny
 * the whole hit, so cancelling damagePre on fabric prevents this damage but cannot change the
 * amount.
 */
@Doc("Fired when a living entity is about to take / just took damage (EntityEvents.damagePre / damagePost).")
@Doc("damagePre is cancellable (return true to negate the damage; on NeoForge the damage becomes 0 and damagePost still fires); damagePost is not cancellable.")
@Getter
public class LivingDamageEventJS {

    @Doc("The entity being damaged.")
    private final LivingEntity entity;

    @Doc("The damage source (vanilla DamageSource object).")
    private final DamageSource source;

    @Doc("The damage amount before mitigation.")
    private final float amount;

    public LivingDamageEventJS(LivingEntity entity, DamageSource source, float amount) {
        this.entity = entity;
        this.source = source;
        this.amount = amount;
    }
}
