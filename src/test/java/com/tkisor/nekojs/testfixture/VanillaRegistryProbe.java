package com.tkisor.nekojs.testfixture;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Distinguishes registry metadata from usable item fixtures with bound vanilla components. */
public final class VanillaRegistryProbe {
    private static volatile Boolean metadataAvailable;
    private static volatile Boolean available;

    private VanillaRegistryProbe() {}

    public static boolean registryMetadataAvailable() {
        Boolean result = metadataAvailable;
        if (result == null) {
            synchronized (VanillaRegistryProbe.class) {
                result = metadataAvailable;
                if (result == null) {
                    try {
                        SharedConstants.tryDetectVersion();
                        Bootstrap.bootStrap();
                        Class.forName("net.minecraft.world.item.Items", true,
                                VanillaRegistryProbe.class.getClassLoader());
                        result = true;
                    } catch (Throwable failure) {
                        result = false;
                    }
                    metadataAvailable = result;
                }
            }
        }
        return result;
    }

    public static boolean available() {
        Boolean result = available;
        if (result == null) {
            synchronized (VanillaRegistryProbe.class) {
                result = available;
                if (result == null) {
                    result = false;
                    if (registryMetadataAvailable()) {
                        try {
                            new ItemStack(Items.STONE).getMaxStackSize();
                            result = true;
                        } catch (Throwable failure) {
                            result = false;
                        }
                    }
                    available = result;
                }
            }
        }
        return result;
    }
}
