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
 * {@code EntityEvents.finalizeSpawn} 的 fabric 挂点：{@code Mob#finalizeSpawn} 的 TAIL。
 *
 * <p>javap 实证（minecraft-merged-deobf-26.1.2.jar）：
 * <ul>
 *   <li>{@code public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(
 *       net.minecraft.world.level.ServerLevelAccessor, net.minecraft.world.DifficultyInstance,
 *       net.minecraft.world.entity.EntitySpawnReason, net.minecraft.world.entity.SpawnGroupData)}
 *       ——26.x 签名（<b>无</b> 1.21.x 的 {@code CompoundTag} 参数；{@code SpawnGroupData}
 *       原样传入/返回），public 具体方法（Mob 抽象类但方法有体），TAIL 合法。</li>
 *   <li>覆盖面：{@code NaturalSpawner} 两处调用点（字节码 441 自然生成 NATURAL、
 *       502 区块生成 CHUNK_GENERATION）与刷怪笼/事件生成同入口；子类覆写
 *       {@code finalizeSpawn} 且不调 {@code super} 时本挂点不触发。</li>
 *   <li><b>为何不可取消/不可改写</b>：{@code NaturalSpawner} 两处调用点均<b>不检查
 *       返回值</b>（441 之后直接 {@code addFreshEntityWithPassengers}；502 同样）——
 *       即使 RETURN 注入改返回 null 也阻止不了生成；fabric 无 NeoForge 那种 patch
 *       生成链的机构，故此总线为通知型。</li>
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
