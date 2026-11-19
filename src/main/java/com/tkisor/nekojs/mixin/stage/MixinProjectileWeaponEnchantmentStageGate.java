package com.tkisor.nekojs.mixin.stage;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tkisor.nekojs.stage.StageEnchantmentUse;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ProjectileWeaponItem.class)
public abstract class MixinProjectileWeaponEnchantmentStageGate {
    @WrapOperation(method = "useAmmo",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;processAmmoUse(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;I)I"))
    private static int nekojs$bindAmmoActor(ServerLevel level, ItemStack weapon, ItemStack ammo, int count,
            Operation<Integer> original, ItemStack firingWeapon, ItemStack projectile, LivingEntity shooter) {
        return StageEnchantmentUse.withActor(shooter, weapon, () -> original.call(level, weapon, ammo, count));
    }

    @WrapOperation(method = "shoot",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ProjectileWeaponItem;createProjectile(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/entity/projectile/Projectile;"))
    private Projectile nekojs$bindProjectileActor(ProjectileWeaponItem item, Level level, LivingEntity shooter,
            ItemStack weapon, ItemStack ammo, boolean critical, Operation<Projectile> original) {
        return StageEnchantmentUse.withActor(shooter, weapon, () -> original.call(item, level, shooter, weapon, ammo, critical));
    }
}
