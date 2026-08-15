package com.tkisor.nekojs.mixin;

import com.tkisor.nekojs.platform.NekoTagLoaderRegistry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagLoader;
import net.minecraft.tags.TagManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 在 TagManager 的 loader lambda（参数直接携带注册表 key 与 TagLoader 实例）里把
 * ResourceKey 捕获进 TagLoader 实例（经 {@link NekoTagLoaderRegistry} duck 接口），
 * 供 build 阶段的 tags 事件分发——1.21.1 的 TagLoader 已不持有该字段（见
 * TagLoaderMixin 注释）。
 *
 * <p>lambda 名绑定 21.1.227 的编译产物；升级 NeoForge 补丁版本时需复核
 * {@code lambda$createLoader$3} 的序号与签名。</p>
 */
@Mixin(TagManager.class)
public abstract class TagManagerMixin {

    @Inject(
            method = "lambda$createLoader$3(Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/tags/TagLoader;Lnet/minecraft/server/packs/resources/ResourceManager;)Lnet/minecraft/tags/TagManager$LoadResult;",
            at = @At("HEAD")
    )
    private static void nekojs$captureRegistryKey(ResourceKey<?> registryKey, TagLoader<?> loader,
                                                  ResourceManager manager, CallbackInfoReturnable<?> cir) {
        ((NekoTagLoaderRegistry) loader).nekojs$setRegistryKey(registryKey);
    }
}
