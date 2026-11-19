package com.tkisor.nekojs.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.tkisor.nekojs.bindings.event.ServerLootReloadExecutor;
import net.minecraft.server.ReloadableServerResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.concurrent.Executor;

@Mixin(ReloadableServerResources.class)
public abstract class ServerLootReloadSourceMixin {
    @Group(name = "lootReloadSource", min = 1, max = 1)
    @ModifyArg(method = "loadResources", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/ReloadableServerRegistries;reload(Lnet/minecraft/core/LayeredRegistryAccess;Ljava/util/List;Lnet/minecraft/server/packs/resources/ResourceManager;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"),
            index = 3, require = 0, allow = 1)
    private static Executor nekojs$withModernApply(Executor prepare, @Local(argsOnly = true, ordinal = 1) Executor apply) {
        return ServerLootReloadExecutor.select(prepare, apply);
    }

    @Group(name = "lootReloadSource", min = 1, max = 1)
    @ModifyArg(method = "loadResources", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/ReloadableServerRegistries;reload(Lnet/minecraft/core/LayeredRegistryAccess;Lnet/minecraft/server/packs/resources/ResourceManager;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"),
            index = 2, require = 0, allow = 1)
    private static Executor nekojs$withLegacyApply(Executor prepare, @Local(argsOnly = true, ordinal = 1) Executor apply) {
        return ServerLootReloadExecutor.select(prepare, apply);
    }
}
