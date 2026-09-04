package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.fabric.event.FabricPlayerEventBindings;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * 玩家物品损坏事件（{@code PlayerEvents.destroyed}，按物品 id dispatch）的 fabric 钩子。
 *
 * <p>26.1.2 平台事实：{@code ItemStack#applyDamage(int, ServerPlayer, Consumer)} 是
 * 碎裂的唯一判定点（setDamageValue → isBroken 分支 → shrink → onBroken.accept）。
 * NeoForge 26.1.2 的 onPlayerDestroyItem 各调用点（挖方块/攻击/物品使用/盾牌格挡/钓鱼竿）
 * 都经此底层，fabric 挂此一点全覆盖；非玩家实体（僵尸手中工具）按
 * {@code player == null} 过滤。
 *
 * <p>注入形态（判据式，无 INVOKE 目标）：
 * <ul>
 *   <li>HEAD：快照进入时 {@code isBroken()}（此时 damage 尚未更新，
 *       {@code processDurabilityChange} 的削抵挡也不影响状态跃迁判据）。</li>
 *   <li>RETURN：对照快照——「进入未碎 && 结束时已碎」= 本次调用把物品耐久打穿，
 *       即碎裂事件（与 isBroken 分支语义等价；对"进入即碎"的复用物件与
 *       "未碎到碎"的跃迁都区分正确）。</li>
 * </ul>
 * 为何不用 INVOKE（shrink/onItemPickup/finishUsingItem 被扫 0 target）：本批次 3 个
 * INVOKE 目标均匹配失败而 2 个成功（initMenu/interactOn），fail 特征未查明（默认
 * {@code defaultRequire=1} 下无法静默跳过），判据式在本仓库更稳。
 */
@Mixin(ItemStack.class)
public abstract class ItemStackDestroyMixin {

    /** applyDamage 入口时的 isBroken() 快照（主线程、不重入，安全）。 */
    @Unique
    private boolean neko$brokenAtEntry;

    @Inject(method = "applyDamage(ILnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V", at = @At("HEAD"))
    private void nekojs$snapshotBroken(int amount, ServerPlayer player, Consumer<Item> onBroken, CallbackInfo ci) {
        neko$brokenAtEntry = ((ItemStack) (Object) this).isBroken();
    }

    @Inject(method = "applyDamage(ILnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V", at = @At("RETURN"))
    private void nekojs$onItemBroken(int amount, ServerPlayer player, Consumer<Item> onBroken, CallbackInfo ci) {
        boolean brokenNow = ((ItemStack) (Object) this).isBroken();
        if (!neko$brokenAtEntry && brokenNow && player != null) {
            FabricPlayerEventBindings.postDestroyed(player, ((ItemStack) (Object) this).copy());
        }
    }
}
