package com.tkisor.nekojs.fabric.mixin.client;

import com.tkisor.nekojs.fabric.client.FabricSpawnEggModels;
import com.tkisor.nekojs.wrapper.registry.gen.EntityTypeBuilder;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Handles eggs with no item definition, which do not enter Fabric's item-model bake callbacks. */
@Mixin(ModelManager.class)
abstract class ModelManagerSpawnEggMixin {
    @Unique
    private volatile Set<Identifier> neko$missingSpawnEggDefinitions = Set.of();

    @Inject(method = "reload", at = @At("RETURN"), cancellable = true)
    private void neko$publishSpawnEggFallbacks(PreparableReloadListener.SharedState state,
            Executor prepareExecutor, PreparableReloadListener.PreparationBarrier barrier, Executor applyExecutor,
            CallbackInfoReturnable<CompletableFuture<Void>> callback) {
        callback.setReturnValue(callback.getReturnValue().thenRun(() ->
                neko$missingSpawnEggDefinitions = FabricSpawnEggModels.missingDefinitions(
                        EntityTypeBuilder.registeredSpawnEggs(), state.resourceManager())));
    }

    @Inject(method = "getItemModel", at = @At("RETURN"), cancellable = true)
    private void neko$resolveSpawnEggModel(Identifier id, CallbackInfoReturnable<ItemModel> callback) {
        callback.setReturnValue(FabricSpawnEggModels.resolve(id, callback.getReturnValue(),
                neko$missingSpawnEggDefinitions, ((ModelManager) (Object) this)::getItemModel));
    }
}
