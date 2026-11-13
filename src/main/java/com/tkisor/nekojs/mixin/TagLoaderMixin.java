// 26.x implementation. This file must not carry version guards again. The 1.21.1 implementation
// is the file of the same name under versions/1.21.1/src; keep it in sync when you change this file.
//? if neoforge {
// TODO(fabric): there is no Fabric implementation yet; drop the whole-file guard once the port is done.
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
     * The 26.x {@link TagLoader} no longer holds the registry key (it keeps only elementLookup and
     * directory). {@code loadPendingTags}, a static method whose parameters carry the {@link Registry},
     * injects the key into a captured field for tag event dispatch in the {@code build} stage. The
     * client network tag sync path has no such field, so tag events fire only while server data packs
     * load; the client should not modify tags anyway.
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
//?}
