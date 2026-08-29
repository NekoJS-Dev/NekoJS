package com.tkisor.nekojs.fabric.event;

import com.tkisor.nekojs.bindings.event.BlockEvents;
import com.tkisor.nekojs.wrapper.event.block.BlockBrokenEventJS;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;

/**
 * B3 SPI 样板的**收益证明**：fabric 侧把原生 Fabric API 事件接到**同一条**
 * {@code BlockEvents.BROKEN} 总线上——脚本写 {@code BlockEvents.broken(...)} 的方式与
 * NeoForge 完全一致，因为总线载荷是加载器中立的 {@link BlockBrokenEventJS}。
 *
 * <p>对照：改造前 {@code BROKEN} 的载荷是 NeoForge 的 {@code BreakBlockEvent}，
 * fabric 根本接不上（没有那个类），只能另开一套脚本 API——那意味着同一个 mod 在两个
 * 加载器上有两套脚本接口，整合包作者得写两份脚本。
 */
public final class FabricBlockEventBindings {

    private FabricBlockEventBindings() {}

    public static void register() {
        // Fabric 的 BEFORE 事件返回 false = 取消破坏，正好对应 NekoJS 脚本 return false
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
                !BlockEvents.BROKEN.post(new BlockBrokenEventJS(level, pos, state, player)));
    }
}
