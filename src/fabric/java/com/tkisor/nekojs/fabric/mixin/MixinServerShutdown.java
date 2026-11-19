package com.tkisor.nekojs.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.tkisor.nekojs.fabric.event.FabricServerEventBindings;
import net.minecraft.server.MinecraftServer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public abstract class MixinServerShutdown {
    @Inject(method = "runServer()V", at = @At(value = "FIELD",
            target = "Lnet/minecraft/server/MinecraftServer;stopped:Z", opcode = Opcodes.PUTFIELD, ordinal = 0),
            require = 1, allow = 1)
    private void nekojs$postStableStopping(CallbackInfo ci) {
        FabricServerEventBindings.postStableStopping((MinecraftServer) (Object) this);
    }

    @WrapOperation(method = "runServer()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;stopServer()V"), require = 3, allow = 3)
    private void nekojs$runServerStop(MinecraftServer server, Operation<Void> original) {
        FabricServerEventBindings.runServerStop(server, () -> original.call(server));
    }

    @WrapOperation(method = "runServer()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;onServerExit()V"), require = 9, allow = 9)
    private void nekojs$finishServerStop(MinecraftServer server, Operation<Void> original,
                                        @Share("stableServerStopped") LocalBooleanRef delivered) {
        try {
            if (!delivered.get()) {
                delivered.set(true);
                FabricServerEventBindings.postStableStopped(server);
            }
        } finally {
            original.call(server);
        }
    }
}
