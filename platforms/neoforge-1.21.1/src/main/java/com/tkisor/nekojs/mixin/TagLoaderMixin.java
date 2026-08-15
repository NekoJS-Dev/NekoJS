package com.tkisor.nekojs.mixin;

import com.tkisor.nekojs.bindings.event.ServerEvents;
import com.tkisor.nekojs.platform.NekoTagLoaderRegistry;
import com.tkisor.nekojs.wrapper.event.server.TagEventJS;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;

@Mixin(TagLoader.class)
public abstract class TagLoaderMixin implements NekoTagLoaderRegistry {

    /**
     * 21.1.x 后期的 {@link TagLoader} 不再持有注册表 key（只剩 idToValue + directory，
     * 与 26.x 同形的 vanilla 重构），key 由 {@link TagManagerMixin} 在 TagManager 的
     * loader lambda（参数直接携带 ResourceKey）经 duck 接口注入捕获，供 build 阶段
     * 事件分发使用。函数 tag 的 loader（ServerFunctionLibrary）不经该路径，保持
     * null → 不触发事件。
     */
    @Unique
    private ResourceKey<?> nekojs$registryKey;

    @Override
    public void nekojs$setRegistryKey(ResourceKey<?> registryKey) {
        this.nekojs$registryKey = registryKey;
    }

    // 描述符锁定公开的 build(Map)：TagLoader 还有一个私有的 build(TagEntry.Lookup, List)
    // 重载，仅按名字选择会命中错误目标（InvalidInjectionException）。
    @Inject(method = "build(Ljava/util/Map;)Ljava/util/Map;", at = @At("HEAD"))
    private void nekojs$fireTagEvent(Map<ResourceLocation, List<TagLoader.EntryWithSource>> map,
                                     CallbackInfoReturnable<?> cir) {
        ResourceKey<?> registryKey = nekojs$registryKey;
        if (registryKey == null) {
            return;
        }
        ResourceLocation registryId = registryKey.location();
        TagEventJS event = new TagEventJS(registryId, map);
        ServerEvents.TAGS.post(event, registryId);
        event.apply();
    }
}
