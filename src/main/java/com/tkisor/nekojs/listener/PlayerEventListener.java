package com.tkisor.nekojs.listener;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.error.NekoErrorTracker;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = NekoJS.MODID)
public class PlayerEventListener {

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (Commands.LEVEL_GAMEMASTERS.check(player.permissions()) && NekoErrorTracker.hasErrors()) {

                int errorCount = NekoErrorTracker.getAllErrors().size();

//                player.sendSystemMessage();

                MutableComponent literal = Component.literal("§c[NekoJS] ⚠ 警告：引擎目前存在 " + errorCount + " 个脚本运行错误。");
                literal.append(Component.literal("\n"));
                MutableComponent dashboardLink = Component.literal("  §a▶ §n[点击此处打开错误列表]")
                        .withStyle(style -> style
                                .withHoverEvent(new HoverEvent.ShowText(Component.literal("§e在全屏列表中统一查看和管理错误")))
                                .withClickEvent(new ClickEvent.RunCommand("/nekojs view_all_errors"))
                        );
                literal.append(dashboardLink);

                player.sendSystemMessage(literal);
            }
        }
    }
}