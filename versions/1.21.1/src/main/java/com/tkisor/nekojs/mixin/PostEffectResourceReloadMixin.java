package com.tkisor.nekojs.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.tkisor.nekojs.client.posteffect.PostEffectManager;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;

import java.util.concurrent.CompletableFuture;

/** Restores a selected resource-backed chain after vanilla replaces the legacy shader resources. */
@Mixin(Minecraft.class)
public abstract class PostEffectResourceReloadMixin {
    @WrapMethod(method = "reloadResourcePacks()Ljava/util/concurrent/CompletableFuture;")
    private CompletableFuture<Void> nekojs$restorePostEffect(Operation<CompletableFuture<Void>> original) {
        Runnable restore = PostEffectManager.captureResourceReloadRestoration();
        CompletableFuture<Void> reload = original.call();
        return restore == null ? reload : reload.thenRunAsync(restore, Minecraft.getInstance());
    }
}
