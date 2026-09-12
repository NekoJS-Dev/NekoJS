// 26.x 实现，本文件不应再出现版本守卫。1.21.1 的实现是 versions/1.21.1/src 下的同名文件，
// 改本文件行为时须同步它。
//? if neoforge {
package com.tkisor.nekojs.listener;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.event.ScriptErrorReporter;
import com.tkisor.nekojs.core.error.NekoErrorUIHelper;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = NekoJS.MODID)
public class PlayerEventListener {

    // 客户端error可能不会立即显示
    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // 错误计数经 ScriptErrorReporter 门面（root-owned ErrorTracker 的静态报告面，
            // 由共享装配函数安装），不再直读 static root
            int errorCount = ScriptErrorReporter.errorCount();
            if (Commands.LEVEL_GAMEMASTERS.check(player.permissions()) && errorCount > 0) {

                player.sendSystemMessage(NekoErrorUIHelper.getErrorComponent(errorCount), false);
            }
            // 挂载物品栏监听器（inventoryChanged 事件）
            InventoryChangeListener.getOrCreate(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        // 克隆（维度切换 / 死亡重生）后重新挂载：新玩家实体的 inventoryMenu 是新的
        InventoryChangeListener.getOrCreate(event.getEntity());
    }
}
//?}
