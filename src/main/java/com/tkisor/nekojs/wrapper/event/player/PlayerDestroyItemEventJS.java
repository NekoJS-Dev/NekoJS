package com.tkisor.nekojs.wrapper.event.player;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * 玩家物品损坏事件（{@code PlayerEvents.destroyed}，按物品 id 分发）的加载器中立载荷。
 *
 * <p>NeoForge 侧对应原生 {@code PlayerDestroyItemEvent}（getOriginal 为损坏前的物品栈拷贝）；
 * 本载荷统一为 {@code itemStack}：损坏触发点（{@code ItemStack#applyDamage} 的碎裂分支）
 * 上的物品栈——伤害值已封顶、出槽前一刻的原数量（可能大于 1，每损坏一个触发一次）。
 *
 * <p>fabric 侧挂点与 NeoForge 全覆盖语义对齐：所有经 {@code ItemStack#hurtAndBreak}
 * 走耐久损耗的物品（挖方块/攻击/物品使用/盾牌格挡/钓鱼竿）在真正碎裂时触发；
 * 非玩家（如僵尸手中的工具）与 infinite materials 会被过程过滤。
 */
@Doc("Fired when a player's item breaks from durability damage (PlayerEvents.destroyed, dispatched by item id).")
@Doc("event.itemStack is the broken stack at the break point (damage capped, count still before the -1 of the broken item).")
@Getter
public class PlayerDestroyItemEventJS {

    @Doc("The player whose item broke.")
    private final ServerPlayer player;

    @Doc("The item stack that broke just before the broken item is removed from it.")
    private final ItemStack itemStack;

    public PlayerDestroyItemEventJS(ServerPlayer player, ItemStack itemStack) {
        this.player = player;
        this.itemStack = itemStack;
    }
}
