package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.fabric.event.FabricEntityEventBindingsV2;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fabric hook for {@code EntityEvents.finalizeSpawn}: the TAIL of {@code Mob#finalizeSpawn}.
 *
 * <p>Checked with javap (minecraft-merged-deobf-26.1.2.jar):
 * <ul>
 *   <li>{@code public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(
 *       net.minecraft.world.level.ServerLevelAccessor, net.minecraft.world.DifficultyInstance,
 *       net.minecraft.world.entity.EntitySpawnReason, net.minecraft.world.entity.SpawnGroupData)}
 *       has the 26.x signature with <b>no</b> 1.21.x {@code CompoundTag} parameter
 *       ({@code SpawnGroupData} is passed in and returned unchanged). It is a public concrete
 *       method on the abstract Mob class, so TAIL is valid.</li>
 *   <li>Coverage: both {@code NaturalSpawner} call sites (bytecode 441 for natural spawning
 *       NATURAL, 502 for CHUNK_GENERATION), plus spawners and event spawning, use the same entry.
 *       A subclass that overrides {@code finalizeSpawn} without calling {@code super} does not
 *       trigger this hook.</li>
 *   <li><b>Why it cannot cancel or change the result</b>: neither {@code NaturalSpawner} call site
 *       <b>checks the return value</b> (441 goes straight on to
 *       {@code addFreshEntityWithPassengers}, and 502 does the same), so even a RETURN injection
 *       that returned null would not prevent the spawn. Fabric has no mechanism like the NeoForge
 *       patch of the spawn chain, so this bus only notifies.</li>
 * </ul>
 */
@Mixin(Mob.class)
public abstract class MixinMobFinalizeSpawn {

    @Inject(method = "finalizeSpawn(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/world/DifficultyInstance;Lnet/minecraft/world/entity/EntitySpawnReason;Lnet/minecraft/world/entity/SpawnGroupData;)Lnet/minecraft/world/entity/SpawnGroupData;",
            at = @At("TAIL"))
    private void nekojs$onFinalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        EntitySpawnReason spawnReason, SpawnGroupData spawnData,
                                        CallbackInfoReturnable<SpawnGroupData> ci) {
        FabricEntityEventBindingsV2.postFinalizeSpawn((Mob) (Object) this, level, difficulty, spawnReason, spawnData);
    }
}
