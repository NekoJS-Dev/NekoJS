package com.tkisor.nekojs.fabric.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tkisor.nekojs.fabric.event.FabricClientEventBindings;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Options.class)
public abstract class MixinOptionsKeyMappings {
    @WrapOperation(method = "<init>(Lnet/minecraft/client/Minecraft;Ljava/io/File;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Options;load()V"), require = 1, allow = 1)
    private void nekojs$beforeInitialOptionsLoad(Options options, Operation<Void> original) {
        FabricClientEventBindings.postInitialKeyMappings();
        original.call(options);
    }
}
