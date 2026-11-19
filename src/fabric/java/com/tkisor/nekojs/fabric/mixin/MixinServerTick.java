package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.fabric.event.FabricServerEventBindings;
import net.minecraft.server.MinecraftServer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public abstract class MixinServerTick {
    @Inject(method = "tickServer(Ljava/util/function/BooleanSupplier;)V",
            at = @At(value = "FIELD", target = "Lnet/minecraft/server/MinecraftServer;tickCount:I",
                    opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER), require = 1, allow = 1)
    private void nekojs$postStableTickPre(CallbackInfo ci) {
        FabricServerEventBindings.postStableTickPre((MinecraftServer) (Object) this);
    }
}
