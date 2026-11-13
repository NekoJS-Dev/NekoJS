//? if >=26 {
package com.tkisor.nekojs.wrapper.event.entity;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * Loader-neutral payload for the mob spawn finalize event
 * ({@code EntityEvents.finalizeSpawn}).
 *
 * <p>The members follow the getters of NeoForge native {@code FinalizeSpawnEvent}
 * (getEntity/getLevel/getDifficulty/getSpawnType/getSpawnData). On fabric,
 * {@code FabricEntityEventBindingsV2} + {@code MixinMobFinalizeSpawn} convert the TAIL of
 * {@code Mob#finalizeSpawn}; natural spawning, chunk spawning and spawners all use the same
 * entry.
 *
 * <p><b>Known difference on fabric (26.1.2, javap-verified)</b>: neither
 * {@code NaturalSpawner} call site of {@code finalizeSpawn} (bytecode 441/502) checks the
 * return value, so even a RETURN injection that rewrites the result to null cannot stop the
 * spawn. The fabric side is therefore notification only: it cannot be cancelled and cannot
 * rewrite the difficulty or spawnData. NeoForge's setSpawnCancelled/setSpawnData/
 * setDifficulty work inside its own patched spawn chain; fabric would need a reworked spawn
 * pipeline to support them.
 */
@Doc("Fired after a mob's spawn data is finalized (EntityEvents.finalizeSpawn). Dispatched by entity type.")
@Doc("Fabric: notification only — not cancellable, spawn data / difficulty cannot be rewritten.")
@Getter
public class MobFinalizeSpawnEventJS {

    @Doc("The mob being spawned.")
    private final Mob mob;

    @Doc("The spawn level.")
    private final ServerLevelAccessor level;

    @Doc("The difficulty of the spawn position.")
    private final DifficultyInstance difficulty;

    @Doc("The spawn reason (natural / chunk generation / spawner / event ...).")
    private final EntitySpawnReason spawnType;

    @Doc("The spawn group data (may be null for natural spawns).")
    private final SpawnGroupData spawnData;

    public MobFinalizeSpawnEventJS(Mob mob, ServerLevelAccessor level, DifficultyInstance difficulty,
                                   EntitySpawnReason spawnType, SpawnGroupData spawnData) {
        this.mob = mob;
        this.level = level;
        this.difficulty = difficulty;
        this.spawnType = spawnType;
        this.spawnData = spawnData;
    }
}
//?}
