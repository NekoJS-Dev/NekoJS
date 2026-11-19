package com.tkisor.nekojs.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tkisor.nekojs.fabric.event.FabricEntityEventBindings;
import com.tkisor.nekojs.fabric.event.FabricEntityEventBindingsV2;
import com.tkisor.nekojs.stage.StageGateEnforcer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/**
 * Fabric hook for {@code EntityEvents.drops} and declarative {@code LootGate} filtering at
 * {@code LivingEntity#dropAllDeathLoot(ServerLevel, DamageSource)}.
 *
 * <p>Captures {@link ItemEntity} instances spawned during {@code dropAllDeathLoot} (whether from
 * vanilla loot tables/equipment or from script-populated {@code event.drops} on cancellation),
 * filters or replaces locked drops via {@link StageGateEnforcer#filterEntityDrops}, and spawns
 * the surviving drops into the {@link ServerLevel}.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityDeathDrops {

    @WrapMethod(method = "dropAllDeathLoot")
    private void nekojs$dispatchDeathDrops(ServerLevel level, DamageSource source, Operation<Void> original) {
        LivingEntity self = (LivingEntity) (Object) this;
        FabricEntityEventBindings.beginDeathDropCapture(self, level);
        List<ItemEntity> drops;
        try {
            original.call(level, source);
        } finally {
            drops = FabricEntityEventBindings.endDeathDropCapture();
        }
        StageGateEnforcer.filterEntityDrops(self, source.getEntity(), drops);
        var selected = FabricEntityEventBindingsV2.collectDrops(self, source, drops);
        if (selected == null) return;
        var retained = new java.util.IdentityHashMap<net.minecraft.world.item.ItemStack, ItemEntity>();
        for (ItemEntity drop : drops) retained.put(drop.getItem(), drop);
        for (var stack : selected) {
            if (stack == null || stack.isEmpty()) continue;
            ItemEntity originalDrop = retained.remove(stack);
            if (originalDrop != null) {
                level.addFreshEntity(originalDrop);
                continue;
            }
            var added = StageGateEnforcer.filterLootStack(
                    com.tkisor.nekojs.stage.StageDamageActors.fromDamageSource(source), null, self, stack);
            if (!added.isEmpty()) self.spawnAtLocation(level, added);
        }
    }

    @Mixin(Entity.class)
    public static abstract class EntitySpawn {
        @WrapOperation(method = "spawnAtLocation(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/entity/item/ItemEntity;",
                at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"),
                require = 1, allow = 1)
        private boolean nekojs$captureSpawnedLoot(ServerLevel level, Entity drop, Operation<Boolean> original) {
            return FabricEntityEventBindings.captureSpawnedDrop((Entity) (Object) this, level, drop)
                    || original.call(level, drop);
        }
    }
}
