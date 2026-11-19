package com.tkisor.nekojs.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tkisor.nekojs.fabric.event.FabricLevelEventBindings;
import com.tkisor.nekojs.fabric.event.FabricServerEventBindings;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public abstract class MixinServerStarting {
    @Inject(method = "loadLevel()V", at = @At("HEAD"), require = 1, allow = 1)
    private void nekojs$postStableAboutToStart(CallbackInfo ci) {
        FabricServerEventBindings.postStableAboutToStart((MinecraftServer) (Object) this);
    }

    @Inject(method = "createLevels()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/storage/ServerLevelData;isInitialized()Z"), require = 1, allow = 1)
    private void nekojs$postReadyOverworld(CallbackInfo ci) {
        FabricLevelEventBindings.postReadyOverworld(((MinecraftServer) (Object) this).overworld());
    }

    @WrapOperation(method = "runServer()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;initServer()Z"),
            require = 1, allow = 1)
    private boolean nekojs$postServerStarting(MinecraftServer server, Operation<Boolean> original) {
        boolean initialized = original.call(server);
        if (initialized) {
            FabricServerEventBindings.postStableStarting(server);
            FabricServerEventBindings.postStableStarted(server);
        }
        return initialized;
    }
}
