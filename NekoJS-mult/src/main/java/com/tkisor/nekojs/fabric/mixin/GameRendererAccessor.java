//? if fabric {
package com.tkisor.nekojs.fabric.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Fabric access seam for the resource-backed post-effect setter. */
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    @Invoker("setPostEffect")
    void nekojs$setPostEffect(Identifier id);
}
//?}
