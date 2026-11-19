package com.tkisor.nekojs.mixin.stage;

import com.tkisor.nekojs.stage.StageDamageActors;
import com.tkisor.nekojs.stage.StageGateEnforcer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({
        LivingEntity.class, ArmorStand.class, EndCrystal.class, EnderDragon.class, EnderDragonPart.class,
        BlockAttachedEntity.class, VehicleEntity.class, ItemEntity.class, ExperienceOrb.class,
        AbstractHurtingProjectile.class, ShulkerBullet.class
})
public abstract class MixinEntityDamageStageGate {
    @Inject(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("HEAD"), cancellable = true)
    private void nekojs$checkDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        Entity target = (Entity) (Object) this;
        if (target.level().isClientSide()) {
            return;
        }
        Player actor = StageDamageActors.fromDamageSource(source);
        if (target instanceof EnderDragonPart part) {
            target = part.parentMob;
        }
        if (actor != null && !StageGateEnforcer.allowDamageEntity(actor, target)) {
            cir.setReturnValue(false);
        }
    }
}
