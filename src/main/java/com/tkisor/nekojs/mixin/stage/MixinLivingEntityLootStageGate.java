package com.tkisor.nekojs.mixin.stage;

import com.llamalad7.mixinextras.sugar.Local;
import com.tkisor.nekojs.stage.StageLootContext;
import com.tkisor.nekojs.stage.StageLootDrop;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Consumer;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityLootStageGate {

    @ModifyArg(
            method = "dropFromLootTable(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;ZLnet/minecraft/resources/ResourceKey;Ljava/util/function/Consumer;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/storage/loot/LootTable;getRandomItems(Lnet/minecraft/world/level/storage/loot/LootParams;JLjava/util/function/Consumer;)V"),
            index = 2
    )
    private Consumer<ItemStack> nekojs$identifyDeathLoot(
            Consumer<ItemStack> consumer,
            @Local(argsOnly = true) ResourceKey<LootTable> table
    ) {
        LivingEntity victim = (LivingEntity) (Object) this;
        return victim.isDeadOrDying() ? StageLootDrop.withTableId(consumer, StageLootContext.tableId(table)) : consumer;
    }
}
