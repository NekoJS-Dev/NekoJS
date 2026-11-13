package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.fabric.event.FabricEntityEventBindingsV2;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabric hook for {@code EntityEvents.drops}: {@code LivingEntity#dropAllDeathLoot}.
 *
 * <p>Why not fabric-api: the javap dump of {@code ServerLivingEntityEvents} 5.0.2 has only
 * ALLOW_DAMAGE, AFTER_DAMAGE, ALLOW_DEATH, AFTER_DEATH and MOB_CONVERSION, with <b>no drop
 * callback</b>.
 *
 * <p>Checked with javap (minecraft-merged-deobf-26.1.2.jar):
 * <ul>
 *   <li>{@code protected void dropAllDeathLoot(net.minecraft.server.level.ServerLevel,
 *       net.minecraft.world.damagesource.DamageSource)} has the 26.x signature (1.21.x had no
 *       ServerLevel parameter) and is a protected concrete method, so HEAD can be injected.</li>
 *   <li>In the bytecode of {@code LivingEntity#die(DamageSource)}, 91-93 set {@code dead=true},
 *       then 109-150 do the server check and call {@code dropAllDeathLoot} before
 *       {@code createWitherRose}. This hook is the single entry for the death drops of every
 *       living entity, players included.</li>
 * </ul>
 *
 * <p>Cancelling: {@code EntityEvents.drops} is cancellable, so a listener returning {@code true}
 * leads to {@code ci.cancel()}, which skips the whole vanilla drop (equipment, loot tables and
 * experience orbs); this matches setCanceled on NeoForge {@code LivingDropsEvent}. Items that
 * scripts push into {@code event.drops} are spawned by
 * {@code FabricEntityEventBindingsV2.postDrops} through {@code Entity#spawnAtLocation} (public,
 * and the bytecode shows it ends in {@code ServerLevel.addFreshEntity}). Limitation: vanilla
 * never builds the drop set up front, because it generates each item as it drops, so the payload's
 * {@code drops} is empty when the event fires (see the {@code LivingDropsEventJS} javadoc).
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityDeathDrops {

    @Inject(method = "dropAllDeathLoot(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;)V",
            at = @At("HEAD"), cancellable = true)
    private void nekojs$onDropAllDeathLoot(ServerLevel level, DamageSource source, CallbackInfo ci) {
        if (FabricEntityEventBindingsV2.postDrops((LivingEntity) (Object) this, level, source)) {
            ci.cancel();
        }
    }
}
