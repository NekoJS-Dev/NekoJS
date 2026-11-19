package com.tkisor.nekojs.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.tkisor.nekojs.bindings.event.ServerLootReloadExecutor;
import net.minecraft.server.ReloadableServerRegistries;
import net.minecraft.world.level.storage.loot.LootDataType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.concurrent.Executor;

@Mixin(ReloadableServerRegistries.class)
public abstract class ServerLootReloadRegistryMixin {
    @ModifyArg(method = {"scheduleElementParse", "scheduleRegistryLoad"}, at = @At(value = "INVOKE",
            target = "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"),
            index = 1, require = 1, allow = 1)
    private static Executor nekojs$selectLootExecutor(Executor prepare, @Local(argsOnly = true) LootDataType<?> type) {
        return ServerLootReloadExecutor.forRegistry(prepare, type);
    }
}
