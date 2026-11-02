//? if neoforge {
package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.platform.Platform;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** Resolves live registries per call so world changes cannot retain old holders. */
final class ItemStackStringRegistries {
    private ItemStackStringRegistries() {}

    static HolderLookup.Provider current() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) return server.registryAccess();
        if (Platform.isClient()) {
            var client = com.tkisor.nekojs.client.ItemStackClientRegistries.current();
            if (client != null) return client;
        }
        return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }
}
//?}
