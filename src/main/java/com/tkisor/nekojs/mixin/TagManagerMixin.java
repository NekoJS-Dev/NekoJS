//? if <26 {
/*package com.tkisor.nekojs.mixin;

import com.tkisor.nekojs.platform.NekoTagLoaderRegistry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagLoader;
import net.minecraft.tags.TagManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/^*
 * Captures the registry ResourceKey into the TagLoader instance from inside the TagManager loader
 * lambda, whose parameters carry the registry key and the TagLoader directly. The key goes in
 * through the {@link NekoTagLoaderRegistry} duck interface and is used for tag event dispatch in the
 * build stage; the 1.21.1 TagLoader no longer holds this field (see the TagLoaderMixin comment).
 *
 * <p>The lambda name is tied to the compiled output of 21.1.227; re-check the index and signature of
 * {@code lambda$createLoader$3} when upgrading the NeoForge patch version.</p>
^/
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
 *///?}
