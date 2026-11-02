package com.tkisor.nekojs.authoring;

import com.tkisor.nekojs.core.lifecycle.NekoRuntimeRoot;
import net.minecraft.client.Minecraft;

/** Loaded only by a bootstrap branch that has already verified the client distribution. */
public final class AuthoringClientPlatform {
    private AuthoringClientPlatform() {}

    public static AuthoringClientExecutor create(NekoRuntimeRoot root) {
        return new AuthoringClientExecutor(
                operation -> Minecraft.getInstance().execute(operation),
                () -> Minecraft.getInstance() != null && Minecraft.getInstance().isSameThread(),
                () -> root.isAuthoringControlActive() && !root.globalState().isClosed()
                        && Minecraft.getInstance() != null);
    }
}
