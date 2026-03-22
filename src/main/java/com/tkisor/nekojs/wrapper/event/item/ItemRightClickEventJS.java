package com.tkisor.nekojs.wrapper.event.item;

import com.tkisor.nekojs.api.event.NekoCancellableEvent;
import com.tkisor.nekojs.bindings.player.NekoPlayerEvent;
import com.tkisor.nekojs.wrapper.entity.PlayerWrapper;
import com.tkisor.nekojs.wrapper.item.ItemStackWrapper;
import lombok.Getter;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public class ItemRightClickEventJS implements NekoPlayerEvent, NekoCancellableEvent {
    private final PlayerInteractEvent.RightClickItem rawEvent;
    @Getter
    private final PlayerWrapper player;
    @Getter
    private final ItemStackWrapper item;

    public ItemRightClickEventJS(PlayerInteractEvent.RightClickItem rawEvent) {
        this.rawEvent = rawEvent;
        this.player = new PlayerWrapper(rawEvent.getEntity());
        this.item = new ItemStackWrapper(rawEvent.getItemStack());
    }

    public MinecraftServer getServer() {
        return rawEvent.getLevel().getServer();
    }

    public Level getLevel() {
        return rawEvent.getLevel();
    }

    @Override
    public PlayerWrapper getEntity() {
        return player;
    }
}