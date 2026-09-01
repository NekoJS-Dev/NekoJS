// 1.21.1 实现，与版本树 src/ 下的同名 26.x 文件成对。内容等于那份文件在本节点求值后的形态
//（可用 tools/extract_evaluated.py 重新提取核对）；26.x 侧行为变更时须同步本文件。
package com.tkisor.nekojs.listener;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.NekoJSMod;
import com.tkisor.nekojs.core.error.NekoErrorUIHelper;
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
            if (player.hasPermissions(2) && NekoJSMod.RUNTIME_ROOT.errors().count() > 0) {

                player.displayClientMessage(NekoErrorUIHelper.getErrorComponent(NekoJSMod.RUNTIME_ROOT.errors().count()), false);
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
