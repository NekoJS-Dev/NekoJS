package com.tkisor.nekojs.listener;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.bindings.event.ItemEvents;
import com.tkisor.nekojs.bindings.event.PlayerEvents;
import com.tkisor.nekojs.core.error.NekoErrorTracker;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = NekoJS.MODID)
public class PlayerEventListener {

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        PlayerEvents.LOGGED_IN.post(event);

        if (event.getEntity() instanceof ServerPlayer player) {
            if (Commands.LEVEL_GAMEMASTERS.check(player.permissions()) && NekoErrorTracker.hasErrors()) {

                int errorCount = NekoErrorTracker.getAllErrors().size();

                player.sendSystemMessage(Component.literal("§c[NekoJS] ⚠ 警告：引擎目前存在 " + errorCount + " 个脚本运行错误。"));

                MutableComponent dashboardLink = Component.literal("  §a▶ §n[点击此处打开错误列表]")
                        .withStyle(style -> style
                                .withHoverEvent(new HoverEvent.ShowText(Component.literal("§e在全屏列表中统一查看和管理错误")))
                                .withClickEvent(new ClickEvent.RunCommand("/nekojs view_all_errors"))
                        );

                player.sendSystemMessage(dashboardLink);
            }
        }
    }

    @SubscribeEvent
    public static void onItemRightClick(PlayerInteractEvent.RightClickItem event) {
        ItemEvents.RIGHT_CLICKED.post(event, event.getItemStack().getItem().toString());
    }

    @SubscribeEvent
    public static void onPlayerChat(ServerChatEvent event) {
        PlayerEvents.CHAT.post(event);
    }

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        ItemEvents.CRAFTED.post(event);
    }

    @SubscribeEvent
    public static void onPlayerPostTick(PlayerTickEvent.Post event) {
        PlayerEvents.TICK_POST.post(event);
    }

    @SubscribeEvent
    public static void onPlayerPreTick(PlayerTickEvent.Pre event) {
        PlayerEvents.TICK_PRE.post(event);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        PlayerEvents.RESPAWNED.post(event);
    }
}