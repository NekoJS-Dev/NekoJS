package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.fabric.event.FabricPlayerEventBindings;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 玩家烧炼事件（{@code PlayerEvents.smelted}，按产物 id dispatch）的 fabric 钩子。
 *
 * <p>NeoForge 26.1.2 触发点：{@code FurnaceResultSlot#checkTakeAchievements} 内
 * {@code if (removeCount != 0) { ... awardUsedRecipesAndPopExperience ...
 * firePlayerSmeltedEvent(player, carried, removeCount) }}（userdev 补丁实证）——
 * 不是「ServerPlayerRecipes 奖励点」：26.1.2 里该 vanilla 类已改名为
 * {@code net.minecraft.stats.ServerRecipeBook}（addRecipes 由
 * {@code ServerPlayer#awardRecipes} 调用，语义是配方书入库而非烧炼取件，会误触发）。
 * fabric 对齐 NeoForge 同一点（熔炉/高炉/烟熏炉取件；营火无取件槽不触发——与 NeoForge 相同）。
 *
 * <p>注入点（javap -c minecraft-merged-deobf-26.1.2 实证）：{@code checkTakeAchievements}
 * 是 protected 具体方法；方法体为 {@code carried.onCraftedBy(player, removeCount)}
 * {@code + awardUsedRecipesAndPopExperience} 后 {@code removeCount = 0} 重置。HEAD 注入
 * （同 NeoForge 的 removeCount != 0 条件；此刻 removeCount 已完成累计、onCraftedBy 尚未执行）。
 * defaultRequire=1：方法被重构时硬失败。
 */
@Mixin(FurnaceResultSlot.class)
public abstract class FurnaceResultSlotMixin {

    @Shadow private int removeCount;

    @Shadow private Player player;

    @Inject(method = "checkTakeAchievements(Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"))
    private void nekojs$onSmeltedTake(ItemStack carried, CallbackInfo ci) {
        if (removeCount > 0) {
            FabricPlayerEventBindings.postSmelted(player, carried);
        }
    }
}
