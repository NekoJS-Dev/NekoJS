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
 * 运行时后处理链接入点（脚本经 {@code PostEffects.register} 注册的内联 GLSL / 链 JSON）：
 *
 * <ul>
 *   <li>{@code getShader} HEAD：命中脚本注册的 shader 源时直接返回，原版资源路径不参与；</li>
 *   <li>{@code getPostChain} HEAD：命中运行时链定义时返回（懒加载 + 缓存），否则放行原版；</li>
 *   <li>{@code apply}/{@code close}：资源 reload / 关闭时丢弃运行时链缓存（链持有 GPU 资源）。</li>
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
