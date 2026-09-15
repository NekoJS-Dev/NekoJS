package com.tkisor.nekojs.fabric.event;

import com.tkisor.nekojs.bindings.event.EntityEvents;
import com.tkisor.nekojs.wrapper.event.entity.EntityLeaveLevelEventJS;
import com.tkisor.nekojs.wrapper.event.entity.EntityTickEventJS;
import com.tkisor.nekojs.wrapper.event.entity.LivingDropsEventJS;
import com.tkisor.nekojs.wrapper.event.entity.MobFinalizeSpawnEventJS;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ServerLevelAccessor;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 实体事件面的 fabric 桥 v2：drops / finalizeSpawn / tickPre / tickPost / leaveLevel——
 * 总线定义在节点孪生 {@link EntityEvents}（组 "EntityEvents" 与 v1 的
 * joinLevel/death/damagePre/damagePost 同名合并），本类只提供投递入口。
 *
 * <p>drops/finalizeSpawn/tick ×2 由 fabric/mixin/* 提供（fabric-api 5.0.2 的
 * entity-events-v1 仅 Elytra/Sleep/Combat/Living 伤害死亡转换，无掉落/生成/tick
 * 回调，javap 实证）；leaveLevel 用 fabric-api
 * {@code ServerEntityEvents.ENTITY_UNLOAD}（注意 FQN 已迁到
 * {@code net.fabricmc.fabric.api.event.lifecycle.v1}）。由于不得修改
 * {@code NekoJSFabricMod}，ENTITY_UNLOAD 的注册由 {@code MixinEntityTick}
 * 首个 tick 时经 {@link #ensureRegistered()} 惰性完成（幂等）。
 */
public final class FabricEntityEventBindingsV2 {

    private static final AtomicBoolean UNLOAD_REGISTERED = new AtomicBoolean(false);

    private FabricEntityEventBindingsV2() {}

    /**
     * 惰性注册 leaveLevel 的 fabric-api 回调（幂等）。在 {@code MixinEntityTick}
     * 第一个实体 tick 时调用——服务端第一个 tick 必然早于任何实体卸载/离开。
     * 后续批次接入 {@code NekoJSFabricMod.onInitialize} 时建议把此调用上移。
     */
    public static void ensureRegistered() {
        if (!UNLOAD_REGISTERED.compareAndSet(false, true)) {
            return;
        }
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) ->
                EntityEvents.LEAVE_LEVEL.post(new EntityLeaveLevelEventJS(entity, level), entity.getType()));
    }

    /**
     * 死亡掉落：{@code MixinLivingEntityDeathDrops} 在
     * {@code LivingEntity#dropAllDeathLoot(ServerLevel, DamageSource)} 入口调用。
     *
     * @return true 表示监听器取消了原版掉落（mixin 应 {@code ci.cancel()}；脚本 push
     *         进 {@code event.drops} 的物品此刻经 {@code Entity#spawnAtLocation} 放出）
     */
    public static boolean postDrops(LivingEntity entity, ServerLevel level, DamageSource source) {
        if (!EntityEvents.DROPS.hasListeners()) {
            return false;
        }
        LivingDropsEventJS payload = new LivingDropsEventJS(entity, source, List.of());
        boolean cancelled = EntityEvents.DROPS.post(payload, entity.getType());
        if (cancelled) {
            for (ItemStack stack : payload.getDrops()) {
                if (!stack.isEmpty()) {
                    entity.spawnAtLocation(level, stack);
                }
            }
        }
        return cancelled;
    }

    /** 生成初始化完成：{@code MixinMobFinalizeSpawn} 在 {@code Mob#finalizeSpawn} 的 TAIL 调用。 */
    public static void postFinalizeSpawn(Mob mob, ServerLevelAccessor level, DifficultyInstance difficulty,
                                         EntitySpawnReason spawnType, SpawnGroupData spawnData) {
        if (!EntityEvents.FINALIZE_SPAWN.hasListeners()) {
            return;
        }
        EntityEvents.FINALIZE_SPAWN.post(
                new MobFinalizeSpawnEventJS(mob, level, difficulty, spawnType, spawnData),
                mob.getType());
    }

    /** 实体 tick 前：{@code MixinEntityTick} 在 {@code Entity#tick} 的 HEAD 调用（已过滤客户端）。 */
    public static void postTickPre(Entity entity) {
        if (!EntityEvents.TICK_PRE.hasListeners()) {
            return;
        }
        EntityEvents.TICK_PRE.post(new EntityTickEventJS(entity, entity.level()), entity.getType());
    }

    /** 实体 tick 后：{@code MixinEntityTick} 在 {@code Entity#tick} 的 TAIL 调用（已过滤客户端）。 */
    public static void postTickPost(Entity entity) {
        if (!EntityEvents.TICK_POST.hasListeners()) {
            return;
        }
        EntityEvents.TICK_POST.post(new EntityTickEventJS(entity, entity.level()), entity.getType());
    }
}
