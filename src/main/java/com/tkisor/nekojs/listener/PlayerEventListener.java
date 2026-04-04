package com.tkisor.nekojs.listener;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.bindings.event.ItemEvents;
import com.tkisor.nekojs.bindings.event.PlayerEvents;
import com.tkisor.nekojs.core.error.NekoErrorTracker;
import com.tkisor.nekojs.core.error.ScriptError;
import com.tkisor.nekojs.wrapper.event.item.ItemCraftedEventJS;
import com.tkisor.nekojs.wrapper.event.item.ItemRightClickEventJS;
import com.tkisor.nekojs.wrapper.event.player.*;
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
        // 1. 触发暴露给 JS 端的登录事件（保持你的原样）
        PlayerLoggedInEventJS eventJS = new PlayerLoggedInEventJS(event);
        PlayerEvents.LOGGED_IN.post(eventJS);

        // 2. OP 进服的错误拦截提醒
        if (event.getEntity() instanceof ServerPlayer player) {
            if (Commands.LEVEL_GAMEMASTERS.check(player.permissions()) && NekoErrorTracker.hasErrors()) {

                int errorCount = NekoErrorTracker.getAllErrors().size();

                // 发送一条主警告，告诉他有几个错误
                player.sendSystemMessage(Component.literal("§c[NekoJS] ⚠ 警告：引擎目前存在 " + errorCount + " 个脚本运行错误。"));

                // 🌟 核心改造：只发一个用来打开 Dashboard 的高亮按钮，不再循环刷屏！
                MutableComponent dashboardLink = Component.literal("  §a▶ §n[点击此处打开错误大盘 UI]")
                        .withStyle(style -> style
                                .withHoverEvent(new HoverEvent.ShowText(Component.literal("§e在全屏列表中统一查看和管理错误")))
                                .withClickEvent(new ClickEvent.RunCommand("/nekojs view_all_errors")) // 调用我们刚才写的看大盘指令
                        );

                player.sendSystemMessage(dashboardLink);
            }
        }
    }

    @SubscribeEvent
    public static void onItemRightClick(PlayerInteractEvent.RightClickItem event) {
        ItemRightClickEventJS eventJS = new ItemRightClickEventJS(event);
        ItemEvents.RIGHT_CLICKED.post(eventJS, eventJS.getItem().getId());
    }

    @SubscribeEvent
    public static void onPlayerChat(ServerChatEvent event) {
        PlayerChatEventJS eventJS = new PlayerChatEventJS(event);
        PlayerEvents.CHAT.post(eventJS);
    }

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        ItemEvents.CRAFTED.post(new ItemCraftedEventJS(event));
    }

    @SubscribeEvent
    public static void onPlayerPostTick(PlayerTickEvent.Post event) {
        PlayerEvents.TICK_POST.post(new PlayerTickPostEventJS(event));
    }

    @SubscribeEvent
    public static void onPlayerPreTick(PlayerTickEvent.Pre event) {
        PlayerEvents.TICK_PRE.post(new PlayerTickPreEventJS(event));
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        PlayerEvents.RESPAWNED.post(new PlayerRespawnEventJS(event));
    }
}