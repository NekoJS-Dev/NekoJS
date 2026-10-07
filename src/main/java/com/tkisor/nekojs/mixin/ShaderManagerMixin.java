// TODO(fabric): fabric 侧还没有对应实现，整文件守卫等移植完成后去掉
//? if neoforge {
//? if >=26 {
package com.tkisor.nekojs.mixin;

import com.mojang.blaze3d.shaders.ShaderType;
import com.tkisor.nekojs.client.posteffect.PostEffectManager;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * Serves active-generation shader sources and cached post chains declared through
 * {@code ClientEvents.postEffects}.
 *
 * <ul>
 *   <li>{@code getShader} HEAD returns an installed shader source when present.</li>
 *   <li>{@code getPostChain} HEAD returns a lazily loaded runtime chain or defers to vanilla.</li>
 *   <li>{@code apply}/{@code close} invalidates cached chains and releases GPU resources.</li>
 * </ul>
 */
@Mixin(ShaderManager.class)
public abstract class ShaderManagerMixin {

    @Shadow @Final private TextureManager textureManager;
    @Shadow @Final private Projection postChainProjection;
    @Shadow @Final private ProjectionMatrixBuffer postChainProjectionMatrixBuffer;

    @Inject(method = "getShader", at = @At("HEAD"), cancellable = true)
    private void nekojs$getRuntimeShader(Identifier id, ShaderType type, CallbackInfoReturnable<String> cir) {
        String source = PostEffectManager.getRuntimeShaderSource(id, type);
        if (source != null) {
            cir.setReturnValue(source);
        }
    }

    @Inject(method = "getPostChain", at = @At("HEAD"), cancellable = true)
    private void nekojs$getRuntimePostChain(Identifier id, Set<Identifier> allowedTargets, CallbackInfoReturnable<PostChain> cir) {
        PostChain chain = PostEffectManager.getOrCreatePostChain(id, allowedTargets,
                this.textureManager, this.postChainProjection, this.postChainProjectionMatrixBuffer);
        if (chain != null) {
            cir.setReturnValue(chain);
        }
    }

    @Inject(method = "apply", at = @At("HEAD"))
    private void nekojs$invalidateOnReload(CallbackInfo ci) {
        PostEffectManager.invalidatePostChainCache();
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void nekojs$invalidateOnClose(CallbackInfo ci) {
        PostEffectManager.invalidatePostChainCache();
    }
}
//?}
//?}
