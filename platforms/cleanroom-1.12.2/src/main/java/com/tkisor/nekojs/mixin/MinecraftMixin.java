package com.tkisor.nekojs.mixin;

import com.tkisor.nekojs.NekoJS;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NekoJS Minecraft mixin - hooks into game initialization.
 * 1.12.2: inject into init() instead of createDisplay().
 */
@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Inject(method = "init", at = @At("RETURN"))
    public void onInitComplete(CallbackInfo ci) {
        NekoJS.LOGGER.info("NekoJS Mixin loaded - Minecraft init complete!");
    }
}
