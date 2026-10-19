package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.fabric.resource.FabricDirectoryPackResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

/** Expands directory groups for initial loads and reloads, retaining native resource-stack semantics. */
@Mixin(MultiPackResourceManager.class)
public abstract class MultiPackResourceManagerMixin {
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true)
    private static List<PackResources> nekojs$expandDirectoryLayers(List<PackResources> packs) {
        return FabricDirectoryPackResources.expand(packs);
    }
}
