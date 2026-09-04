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
 * 生物生成初始化完成事件（{@code EntityEvents.finalizeSpawn}）的**加载器中立**载荷。
 *
 * <p>成员对齐 NeoForge 原生 {@code FinalizeSpawnEvent} 的 getter
 * （getEntity/getLevel/getDifficulty/getSpawnType/getSpawnData）。fabric 侧由
 * {@code FabricEntityEventBindingsV2} + {@code MixinMobFinalizeSpawn} 从
 * {@code Mob#finalizeSpawn} 的 TAIL 转换（自然生成/区块生成/刷怪笼等所有生成原因同入口）。
 *
 * <p><b>已知差异（fabric，26.1.2 javap 实证）</b>：{@code NaturalSpawner} 两处
 * {@code finalizeSpawn} 调用点（字节码 441/502）均<b>不检查返回值</b>——即使
 * 以 RETURN 注入改写返回 null 也无法阻止生成。因此 fabric 侧为**通知型**（不可
 * 取消、不可改写难度/spawnData）；NeoForge 的 setSpawnCancelled/setSpawnData/
 * setDifficulty 在其自身 patch 的生成链中生效，fabric 需自行 rework 生成管线才能
 * 支持（超出本批次）。
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
