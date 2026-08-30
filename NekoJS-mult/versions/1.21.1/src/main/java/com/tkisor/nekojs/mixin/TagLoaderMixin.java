// 1.21.1 实现，与版本树 src/ 下的同名 26.x 文件成对。内容等于那份文件在本节点求值后的形态
//（可用 tools/extract_evaluated.py 重新提取核对）；26.x 侧行为变更时须同步本文件。
package com.tkisor.nekojs.mixin;

import com.tkisor.nekojs.bindings.event.ServerEvents;
import com.tkisor.nekojs.wrapper.event.server.TagEventJS;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;
import java.util.Map;
import com.tkisor.nekojs.platform.NekoTagLoaderRegistry;

@Mixin(TagLoader.class)
public abstract class TagLoaderMixin implements NekoTagLoaderRegistry {

    /**
     * 26.x 的 {@link TagLoader} 不再持有注册表 key（只剩 elementLookup + directory），
     * 由 {@code loadPendingTags}（静态方法，参数带 {@link Registry}）注入捕获，
     * 供 {@code build} 阶段事件分发使用。客户端网络 tag 同步路径没有该字段，
     * 因此 tag 事件只在服务端数据包加载阶段触发（客户端本来就不应修改 tag）。
     */
    @Unique
    private ResourceKey<?> nekojs$registryKey;

    @Override
    public void nekojs$setRegistryKey(ResourceKey<?> registryKey) {
        this.nekojs$registryKey = registryKey;
    }

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
