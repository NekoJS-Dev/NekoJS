package com.tkisor.nekojs.client;

import com.tkisor.nekojs.platform.Platform;
import net.minecraft.client.Minecraft;

public final class ClientEventThread {
    private ClientEventThread() {}

    public static boolean isCurrent() {
        return Platform.isClient() && isClientMainThread();
    }

    private static boolean isClientMainThread() {
        Minecraft client = Minecraft.getInstance();
        return client != null && client.isSameThread();
    }
}
