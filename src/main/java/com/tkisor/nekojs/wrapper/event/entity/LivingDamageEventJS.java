package com.tkisor.nekojs.wrapper.event.entity;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * 生物受伤事件（{@code EntityEvents.damagePre / damagePost}）的加载器中立载荷，
 * 成员对齐契约承诺集（entity / source / amount）。
 *
 * <p>NeoForge 侧直传原生 {@code LivingDamageEvent.Pre/Post}（成员同形）；fabric 侧由
 * {@code FabricEntityEventBindings} 从 {@code ServerLivingEntityEvents} 转换。
 *
 * <p><b>已知能力差异</b>：NeoForge 的 Pre 允许脚本改写伤害量（{@code setNewDamageAmount}），
 * fabric 的 {@code ALLOW_DAMAGE} 只能整体放行/拒绝——damagePre 在 fabric 上仅支持取消
 * （监听器返回 {@code true} = 免除本次伤害），不支持改数值。
 */
@Doc("Fired when a living entity is about to take / just took damage (EntityEvents.damagePre / damagePost).")
@Doc("damagePre is cancellable (return true to negate the damage); damagePost is not.")
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
