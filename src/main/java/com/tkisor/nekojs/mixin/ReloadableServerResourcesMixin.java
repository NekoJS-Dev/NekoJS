package com.tkisor.nekojs.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.tkisor.nekojs.api.recipe.IRecipeManagerExtension;
import com.tkisor.nekojs.resource.RecipeTypeDefinitionResources;
import com.tkisor.nekojs.stage.StageDomainOwner;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ReloadableServerResources.class)
public abstract class ReloadableServerResourcesMixin {

    @Final
    @Shadow private RecipeManager recipes;

    @WrapOperation(method = "lambda$loadResources$2", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/event/EventHooks;onResourceReload(Lnet/minecraft/server/ReloadableServerResources;Lnet/minecraft/core/RegistryAccess;Ljava/util/Map;)Ljava/util/List;"), require = 0)
    private static List<PreparableReloadListener> nekojs$trackModernResourceAttempt(
            ReloadableServerResources incoming, RegistryAccess registries, Map<?, ?> listeners,
            Operation<List<PreparableReloadListener>> original,
            @Local(argsOnly = true) ResourceManager source, @Local(argsOnly = true, ordinal = 1) Executor apply) {
        StageDomainOwner owner = StageDomainOwner.current();
        if (owner != null) owner.trackResourceAttempt(source, incoming, apply, registries);
        return original.call(incoming, registries, listeners);
    }

    @WrapOperation(method = "lambda$loadResources$5", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/event/EventHooks;onResourceReload(Lnet/minecraft/server/ReloadableServerResources;Lnet/minecraft/core/RegistryAccess;)Ljava/util/List;"), require = 0)
    private static List<PreparableReloadListener> nekojs$trackLegacyResourceAttempt(
            ReloadableServerResources incoming, RegistryAccess registries,
            Operation<List<PreparableReloadListener>> original,
            @Local(argsOnly = true) ResourceManager source, @Local(argsOnly = true, ordinal = 1) Executor apply) {
        StageDomainOwner owner = StageDomainOwner.current();
        if (owner != null) owner.trackResourceAttempt(source, incoming, apply, registries);
        return original.call(incoming, registries);
    }

    @Inject(method = "loadResources", at = @At("RETURN"), cancellable = true)
    private static void nekojs$discardFailedResourceAttempt(CallbackInfoReturnable<CompletableFuture<ReloadableServerResources>> ci,
            @Local(argsOnly = true) ResourceManager source) {
        StageDomainOwner owner = StageDomainOwner.current();
        if (owner == null) return;
        ci.setReturnValue(ci.getReturnValue().whenComplete((result, failure) -> {
            if (failure == null) return;
            try {
                owner.abortResourceLoad(source);
            } catch (RuntimeException cleanup) {
                if (cleanup != failure) failure.addSuppressed(cleanup);
            }
        }));
    }

    @WrapMethod(method = {"updateComponentsAndStaticRegistryTags", "updateRegistryTags"}, require = 1)
    private void nekojs$releaseResourceAttempt(Operation<Void> original) {
        StageDomainOwner owner = StageDomainOwner.current();
        try {
            original.call();
        } finally {
            if (owner != null) owner.abortResourceBinding((ReloadableServerResources) (Object) this);
        }
    }

//? if >=26 {
    @Inject(method = "updateComponentsAndStaticRegistryTags", at = @At("TAIL"))
    private void onTagsAndComponentsBound(CallbackInfo ci) {
//?} else {
    /*@Inject(
            method = "updateRegistryTags()V",
            at = @At("TAIL")
    )
    private void onTagsUpdated(CallbackInfo ci) {
*///?}
        if (this.recipes instanceof RecipeTypeDefinitionResources.Prepared prepared) {
            prepared.nekojs$publishDataPackSchemas();
        }
        StageDomainOwner owner = StageDomainOwner.current();
        if (owner != null && owner.finishResourceScripts((ReloadableServerResources) (Object) this, this.recipes)) return;
        if (this.recipes instanceof IRecipeManagerExtension ext) {
            ext.nekojs$applyScripts();
        }
    }
}
