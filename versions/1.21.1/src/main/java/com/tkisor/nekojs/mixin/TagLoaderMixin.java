// 1.21.1 implementation, paired with the same-named 26.x file under the version tree src/. Its
// content is that file as evaluated at this node. Keep this file in sync when the 26.x side changes.
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
     * The 26.x {@link TagLoader} no longer holds the registry key (it keeps only elementLookup and
     * directory), so {@code loadPendingTags} (a static method whose parameter carries the
     * {@link Registry}) captures it by injection for event dispatch in the {@code build} stage. The
     * client network tag sync path has no such field, so tag events fire only while the server loads
     * datapacks; the client is not supposed to modify tags anyway.
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
