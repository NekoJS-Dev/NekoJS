package com.tkisor.nekojs.bindings.level;

import com.tkisor.nekojs.bindings.event.NekoEvent;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public interface NekoLevelEvent extends NekoEvent {
    Level getLevel();

    @Nullable
    default MinecraftServer getServer() {
        return getLevel().getServer();
    }

    default RegistryAccess getRegistries() {
        return getLevel().registryAccess();
    }
}
