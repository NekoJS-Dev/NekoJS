// TODO(loader-port): deferred to the LoaderBridge fabric port
//? if neoforge {
package com.tkisor.nekojs.mixin;

//? if >=26 {
import com.llamalad7.mixinextras.sugar.Local;
//?}
import com.tkisor.nekojs.bindings.event.ServerEvents;
import com.tkisor.nekojs.wrapper.event.server.TagEventJS;
//? if >=26 {
import net.minecraft.core.Registry;
//?}
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
//? if >=26 {
import net.minecraft.server.packs.resources.ResourceManager;
//?}
import net.minecraft.tags.TagLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;
import java.util.Map;
//? if <26 {
/*import com.tkisor.nekojs.platform.NekoTagLoaderRegistry;
*///?}

@Mixin(TagLoader.class)
//? if >=26 {
public abstract class TagLoaderMixin {
//?} else {
/*public abstract class TagLoaderMixin implements NekoTagLoaderRegistry {
*///?}

    /**
     * 26.x 的 {@link TagLoader} 不再持有注册表 key（只剩 elementLookup + directory），
     * 由 {@code loadPendingTags}（静态方法，参数带 {@link Registry}）注入捕获，
     * 供 {@code build} 阶段事件分发使用。客户端网络 tag 同步路径没有该字段，
     * 因此 tag 事件只在服务端数据包加载阶段触发（客户端本来就不应修改 tag）。
     */
    @Unique
    private ResourceKey<?> nekojs$registryKey;

//? if >=26 {
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
//?} else {
/*    @Override
    public void nekojs$setRegistryKey(ResourceKey<?> registryKey) {
        this.nekojs$registryKey = registryKey;
*///?}
    }

//? if >=26 {
    @Inject(method = "build", at = @At("HEAD"))
//?} else {
/*    @Inject(method = "build(Ljava/util/Map;)Ljava/util/Map;", at = @At("HEAD"))
*///?}
    private void nekojs$fireTagEvent(Map<Identifier, List<TagLoader.EntryWithSource>> map,
                                     CallbackInfoReturnable<?> cir) {
        ResourceKey<?> registryKey = nekojs$registryKey;
        if (registryKey == null) {
            return;
        }
//? if >=26 {
        Identifier registryId = registryKey.identifier();
//?} else {
/*        Identifier registryId = registryKey.location();
*///?}
        TagEventJS event = new TagEventJS(registryId, map);
        ServerEvents.TAGS.post(event, registryId);
        event.apply();
    }
}
//?}
