//? if <26 {
/*package com.tkisor.nekojs.platform;

import net.minecraft.resources.ResourceKey;

/^*
 * Duck interface implemented by {@code TagLoaderMixin} and written by {@code TagManagerMixin}: it
 * attaches a registry key to a TagLoader instance from another mixin class, following the same idea
 * as the interface injection in inject Extension. It has to live outside the mixin configuration
 * package, because Sponge refuses to load non-mixin classes directly from a mixin package
 * (IllegalClassLoadError), so it belongs to an internal platform bridge package.
^/
public interface NekoTagLoaderRegistry {

void nekojs$setRegistryKey(ResourceKey<?> registryKey);
}
 *///?}
