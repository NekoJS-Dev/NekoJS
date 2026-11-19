package com.tkisor.nekojs.bindings.event;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.loot.LootDataType;

import java.util.concurrent.Executor;

public record ServerLootReloadExecutor(Executor prepare, MinecraftServer owner) implements Executor {
    private static final ThreadLocal<MinecraftServer> ACTIVE_OWNER = new ThreadLocal<>();

    public static boolean isStableDispatch() {
        MinecraftServer server = ACTIVE_OWNER.get();
        return server != null && server.isSameThread();
    }

    public static Executor select(Executor prepare, Executor apply) {
        return apply instanceof MinecraftServer server && ServerEvents.hasStableLootTableLoadListeners()
                ? new ServerLootReloadExecutor(prepare, server) : prepare;
    }

    public static Executor forRegistry(Executor executor, LootDataType<?> dataType) {
        if (executor instanceof ServerLootReloadExecutor reload) {
            return dataType == LootDataType.TABLE ? reload::executeLootTask : reload.prepare;
        }
        return executor;
    }

    private void executeLootTask(Runnable command) {
        owner.execute(() -> {
            MinecraftServer previous = ACTIVE_OWNER.get();
            ACTIVE_OWNER.set(owner);
            try {
                command.run();
            } finally {
                if (previous == null) ACTIVE_OWNER.remove();
                else ACTIVE_OWNER.set(previous);
            }
        });
    }

    @Override
    public void execute(Runnable command) {
        prepare.execute(command);
    }
}
