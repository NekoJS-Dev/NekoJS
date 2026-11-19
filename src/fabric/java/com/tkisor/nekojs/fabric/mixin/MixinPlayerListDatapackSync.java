package com.tkisor.nekojs.fabric.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.tkisor.nekojs.fabric.event.FabricCommandEventBindings;
import com.tkisor.nekojs.fabric.event.FabricServerEventBindings;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerList.class)
public abstract class MixinPlayerListDatapackSync {
    @Inject(method = "placeNewPlayer", at = @At("RETURN"))
    private void nekojs$loggedIn(Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo ci) {
        FabricServerEventBindings.onPlayerLoggedIn(player);
    }

    @Inject(method = "remove", at = @At("HEAD"))
    private void nekojs$loggedOut(ServerPlayer player, CallbackInfo ci) {
        FabricServerEventBindings.onPlayerLoggedOut(player);
    }

    @Inject(method = "respawn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;getRespawnConfig()Lnet/minecraft/server/level/ServerPlayer$RespawnConfig;"))
    private void nekojs$respawned(ServerPlayer oldPlayer, boolean alive, Entity.RemovalReason removalReason,
                                  CallbackInfoReturnable<ServerPlayer> cir, @Local(index = 6) ServerPlayer newPlayer) {
        FabricServerEventBindings.onPlayerRespawned(oldPlayer, newPlayer, alive);
    }

    @Inject(method = "reloadResources", at = @At("HEAD"))
    private void nekojs$postInstalledCommands(CallbackInfo ci) {
        FabricCommandEventBindings.postInstalledRegistration(((PlayerList) (Object) this).getServer());
    }

    @Inject(method = "placeNewPlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;getRecipeManager()Lnet/minecraft/world/item/crafting/RecipeManager;"))
    private void nekojs$loginDatapackSync(Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo ci) {
        FabricServerEventBindings.postStableDatapackSync((PlayerList) (Object) this, player);
    }

    @Inject(method = "reloadResources", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/tags/TagNetworkSerialization;serializeTagsToNetwork(Lnet/minecraft/core/LayeredRegistryAccess;)Ljava/util/Map;"))
    private void nekojs$broadcastDatapackSync(CallbackInfo ci) {
        FabricServerEventBindings.postStableDatapackSync((PlayerList) (Object) this, null);
    }
}
