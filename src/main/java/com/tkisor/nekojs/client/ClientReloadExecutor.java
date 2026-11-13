//? if neoforge {
package com.tkisor.nekojs.client;

import net.minecraft.client.Minecraft;
import com.tkisor.nekojs.platform.compat.McPlatformCompat;

/**
 * Thread ownership for a CLIENT script reload. The client Context is owned by the client main thread
 * (the render thread): event dispatch, timer flush and the F3+T resource reload all happen there.
 * In single player, {@code /nekojs reload client} runs on the integrated server thread, and reloading
 * in place would let two threads touch the same Context one after the other (the external synchronized
 * keeps the order, but every new entry point has to reason about it again). This class hands the work
 * to the client main thread instead, so a CLIENT Context has exactly one owner thread.
 *
 * <p>On a dedicated server {@link #isClientDist()} is false and callers do not go through
 * {@link #execute}; {@code Minecraft} is only touched inside execute, so initializing this class is
 * safe on the server side.
 */
public final class ClientReloadExecutor {

    private ClientReloadExecutor() {}

    public static boolean isClientDist() {
        // McPlatformCompat hides the per-version differences of the dist accessor (this file is the neoforge side)
        return McPlatformCompat.get().isClientDist();
    }

    /**
     * Runs the task on the client main thread. The command side returns immediately; the reload
     * flow reports the result through its own broadcast and logs.
     */
    public static void execute(Runnable task) {
        Minecraft.getInstance().execute(task);
    }
}
//?}
