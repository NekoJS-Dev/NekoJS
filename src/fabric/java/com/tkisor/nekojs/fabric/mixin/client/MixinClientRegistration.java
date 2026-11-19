package com.tkisor.nekojs.fabric.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tkisor.nekojs.fabric.event.FabricClientEventBindings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.particle.ParticleResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.util.Unit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(Minecraft.class)
public abstract class MixinClientRegistration {
    @WrapOperation(method = "<init>(Lnet/minecraft/client/main/GameConfig;)V", at = @At(value = "NEW",
            target = "(Lnet/minecraft/client/Minecraft;Ljava/io/File;)Lnet/minecraft/client/Options;"))
    private static Options nekojs$loadBeforeOptions(Minecraft minecraft, File directory, Operation<Options> original) {
        FabricClientEventBindings.beforeInitialOptions();
        return original.call(minecraft, directory);
    }

    @WrapOperation(method = "<init>(Lnet/minecraft/client/main/GameConfig;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/particle/ParticleResources;onReload(Ljava/lang/Runnable;)V"),
            require = 1, allow = 1)
    private static void nekojs$afterParticleReloadRegistration(ParticleResources resources, Runnable listener,
                                                               Operation<Void> original) {
        original.call(resources, listener);
        FabricClientEventBindings.postInitialParticleProviders();
    }

    @WrapOperation(method = "<init>(Lnet/minecraft/client/main/GameConfig;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/packs/resources/ReloadableResourceManager;createReload(Ljava/util/concurrent/Executor;Ljava/util/concurrent/Executor;Ljava/util/concurrent/CompletableFuture;Ljava/util/List;)Lnet/minecraft/server/packs/resources/ReloadInstance;"))
    private static ReloadInstance nekojs$beforeReload(ReloadableResourceManager manager, Executor prepare, Executor apply,
                                                      CompletableFuture<Unit> barrier, List<PackResources> packs,
                                                      Operation<ReloadInstance> original) {
        FabricClientEventBindings.beforeInitialResourceReload();
        return original.call(manager, prepare, apply, barrier, packs);
    }
}
