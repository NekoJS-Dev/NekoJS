package com.tkisor.nekojs.listener;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.bindings.event.ItemEvents;
import com.tkisor.nekojs.bindings.event.PlayerEvents;
import com.tkisor.nekojs.core.error.NekoErrorTracker;
import com.tkisor.nekojs.core.error.ScriptError;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.wrapper.event.item.ItemCraftedEventJS;
import com.tkisor.nekojs.wrapper.event.item.ItemRightClickEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerChatEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerLoggedInEventJS;
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

@EventBusSubscriber(modid = NekoJS.MODID)
public class PlayerEventListener {
    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        PlayerLoggedInEventJS eventJS = new PlayerLoggedInEventJS(event);
         PlayerEvents.LOGGED_IN.post(eventJS);

        if (event.getEntity() instanceof ServerPlayer player) {
            if (Commands.LEVEL_GAMEMASTERS.check(player.permissions()) && NekoErrorTracker.hasErrors()) {

                player.sendSystemMessage(Component.literal("§c[NekoJS] ⚠ 警告：当前环境存在 " + NekoErrorTracker.getAllErrors().size() + " 个脚本运行错误！"));

                for (ScriptError error : NekoErrorTracker.getAllErrors()) {
                    String idStr = error.getScript().id.toString();
                    String pathStr = NekoJSPaths.ROOT.relativize(error.getScript().path).toString().replace('\\', '/');

                    MutableComponent link = Component.literal("  §4▶ §c" + pathStr + " §8(第 " + error.getLineNumber() + " 行)")
                            .withStyle(style -> style
                                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("§e点击在全屏 UI 中查看堆栈详情")))
                                    .withClickEvent(new ClickEvent.RunCommand("/nekojs view_error " + idStr))
                            );
                    player.sendSystemMessage(link);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onItemRightClick(PlayerInteractEvent.RightClickItem event) {
        ItemRightClickEventJS eventJS = new ItemRightClickEventJS(event);
        ItemEvents.RIGHT_CLICKED.post(eventJS.getItem().getId(), eventJS);
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

}