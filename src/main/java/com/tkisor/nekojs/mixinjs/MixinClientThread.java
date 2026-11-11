package com.tkisor.nekojs.mixinjs;

import net.minecraft.client.Minecraft;

/** Referenced only after the platform has established a physical client distribution. */
final class MixinClientThread {
    private MixinClientThread() {}
    static boolean isCurrent() {
        return Minecraft.getInstance() != null && Minecraft.getInstance().isSameThread();
    }
}
