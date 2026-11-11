package dev.latvian.mods.kubejs.level;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

/** Native level accessors retain their real Minecraft return descriptors. */
public interface KubeLevelEvent extends KubeEvent {
    Level getLevel();

    default MinecraftServer getServer() {
        return getLevel().getServer();
    }

    default RegistryAccess getRegistries() {
        return getLevel().registryAccess();
    }
}
