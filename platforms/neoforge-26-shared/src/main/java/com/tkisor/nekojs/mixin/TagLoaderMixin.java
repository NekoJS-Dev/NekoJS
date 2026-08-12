package com.tkisor.nekojs.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.tkisor.nekojs.bindings.event.ServerEvents;
import com.tkisor.nekojs.wrapper.event.server.TagEventJS;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;

@Mixin(TagLoader.class)
public abstract class TagLoaderMixin {

    /**
     * 26.x 的 {@link TagLoader} 不再持有注册表 key（只剩 elementLookup + directory），
     * 由 {@code loadPendingTags}（静态方法，参数带 {@link Registry}）注入捕获，
     * 供 {@code build} 阶段事件分发使用。客户端网络 tag 同步路径没有该字段，
     * 因此 tag 事件只在服务端数据包加载阶段触发（客户端本来就不应修改 tag）。
     */
    @Unique
    private ResourceKey<?> nekojs$registryKey;

    @Inject(
            method = "loadPendingTags(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/core/Registry;)Ljava/util/Optional;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/tags/TagLoader;load(Lnet/minecraft/server/packs/resources/ResourceManager;)Ljava/util/Map;",
                    shift = At.Shift.BEFORE
            )
    )
    private static void nekojs$captureRegistryKey(ResourceManager manager, Registry<?> registry,
                                                  CallbackInfoReturnable<?> cir,
                                                  @Local(name = "loader") TagLoader<?> loader) {
        ((TagLoaderMixin) (Object) loader).nekojs$registryKey = registry.key();
    }

    @Inject(method = "build", at = @At("HEAD"))
    private void nekojs$fireTagEvent(Map<Identifier, List<TagLoader.EntryWithSource>> map,
                                     CallbackInfoReturnable<?> cir) {
        ResourceKey<?> registryKey = nekojs$registryKey;
        if (registryKey == null) {
            return;
        }
        Identifier registryId = registryKey.identifier();
        TagEventJS event = new TagEventJS(registryId, map);
        ServerEvents.TAGS.post(event, registryId);
        event.apply();
    }
}
