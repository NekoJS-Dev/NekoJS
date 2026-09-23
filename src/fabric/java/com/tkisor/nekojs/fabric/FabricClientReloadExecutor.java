package com.tkisor.nekojs.fabric;

/** Schedules CLIENT runtime lifecycle work on the Minecraft client thread. */
public final class FabricClientReloadExecutor {

    private FabricClientReloadExecutor() {}

    public static void execute(Runnable task) {
        net.minecraft.client.Minecraft.getInstance().execute(task);
    }
}
