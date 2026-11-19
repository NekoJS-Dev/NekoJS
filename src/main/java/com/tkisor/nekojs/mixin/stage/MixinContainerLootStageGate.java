package com.tkisor.nekojs.mixin.stage;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.tkisor.nekojs.stage.StageLootContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(RandomizableContainer.class)
public interface MixinContainerLootStageGate {

    @ModifyArg(
            method = "unpackLootTable",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/ReloadableServerRegistries$Holder;getLootTable(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/world/level/storage/loot/LootTable;"),
            index = 0
    )
    private static ResourceKey<LootTable> nekojs$captureLootTable(
            ResourceKey<LootTable> key,
            @Share("lootTable") LocalRef<ResourceKey<LootTable>> table
    ) {
        table.set(key);
        return key;
    }

    @ModifyArg(
            method = "unpackLootTable",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/storage/loot/LootTable;fill(Lnet/minecraft/world/Container;Lnet/minecraft/world/level/storage/loot/LootParams;J)V"),
            index = 1
    )
    private static LootParams nekojs$passContainerLootOrigin(
            LootParams params,
            @Local(argsOnly = true) Player actor,
            @Share("lootTable") LocalRef<ResourceKey<LootTable>> table
    ) {
        ((StageLootContext) (Object) params).nekojs$setLootOrigin(StageLootContext.tableId(table.get()), actor);
        return params;
    }
}
