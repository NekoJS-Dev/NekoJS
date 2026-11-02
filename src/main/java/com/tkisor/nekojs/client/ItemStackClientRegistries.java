package com.tkisor.nekojs.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;

/** Client-only access is reached only after the platform side check. */
public final class ItemStackClientRegistries {
    private ItemStackClientRegistries() {}

    public static HolderLookup.Provider current() {
        var connection = Minecraft.getInstance().getConnection();
        return connection == null ? null : connection.registryAccess();
    }
}
