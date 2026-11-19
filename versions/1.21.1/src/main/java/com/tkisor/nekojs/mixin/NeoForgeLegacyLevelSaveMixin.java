package com.tkisor.nekojs.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.tkisor.nekojs.bindings.event.NeoForgeLevelSaveRouting;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class NeoForgeLegacyLevelSaveMixin {
    @WrapOperation(method = "save(Lnet/minecraft/util/ProgressListener;ZZ)V", at = @At(value = "INVOKE", remap = false,
            target = "Lnet/neoforged/bus/api/IEventBus;post(Lnet/neoforged/bus/api/Event;)Lnet/neoforged/bus/api/Event;"),
            require = 1, allow = 1)
    private Event nekojs$postBeforeFlush(IEventBus bus, Event event, Operation<Event> original,
                                         @Local(argsOnly = true, ordinal = 0) boolean flush,
                                         @Share("deferredLevelSave") LocalRef<LevelEvent.Save> deferred) {
        if (!flush || !(event instanceof LevelEvent.Save save)) return original.call(bus, event);
        deferred.set(save);
        return NeoForgeLevelSaveRouting.postBeforeFlush(save, () -> original.call(bus, event));
    }

    @Inject(method = "save(Lnet/minecraft/util/ProgressListener;ZZ)V", at = @At("TAIL"), require = 1, allow = 1)
    private void nekojs$postAfterFlush(CallbackInfo ci, @Share("deferredLevelSave") LocalRef<LevelEvent.Save> deferred) {
        NeoForgeLevelSaveRouting.postAfterFlush(deferred.get());
    }
}
