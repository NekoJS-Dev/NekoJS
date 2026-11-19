package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.fabric.event.FabricServerEventBindings;
import net.minecraft.server.ReloadableServerResources;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ReloadableServerResources.class)
public abstract class MixinReloadableServerResourcesTags {
    @Inject(method = "updateComponentsAndStaticRegistryTags()V", at = @At(value = "FIELD",
            target = "Lnet/minecraft/server/ReloadableServerResources;newComponents:Ljava/util/List;", opcode = Opcodes.GETFIELD),
            require = 1, allow = 1)
    private void nekojs$afterTagsBound(CallbackInfo ci) {
        FabricServerEventBindings.postStableTagsUpdated((ReloadableServerResources) (Object) this);
    }
}
