package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.fabric.event.FabricPlayerEventBindings;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 玩家合成事件（{@code PlayerEvents.crafted}，按产物 id dispatch）的 fabric 钩子。
 *
 * <p>NeoForge 26.1.2 触发点：{@code ResultSlot#checkTakeAchievements} 内
 * {@code if (removeCount > 0)} 块、{@code carried.onCraftedBy(...)} 之后（userdev 补丁实证：
 * firePlayerCraftingEvent(player, carried, craftSlots)）。fabric 对齐同一点。
 *
 * <p>注入点（javap -c minecraft-merged-deobf-26.1.2 实证）：{@code checkTakeAchievements}
 * 是 protected 具体方法（非 abstract，无 TAIL 落在抽象方法的教训问题）；方法体开头即
 * removeCount 偏移 4 的 ifle 守卫。HEAD 注入发生在 onCraftedBy/reward 之前（与 NeoForge
 * 的「之后」仅一线之隔，载荷成员一致）；共享树 ResultSlotMixin（com.tkisor.nekojs.mixin）
 * 注入的是 {@code getRemainingItems}——不同类不同方法，互不冲突。
 *
 * <p>守卫：与 NeoForge 相同的 {@code removeCount > 0}——onQuickCraft/onTake 都会先累计
 * removeCount 再进本方法，空取件（removeCount==0）不发事件。
 * defaultRequire=1：方法被重构时硬失败。
 */
@Mixin(ResultSlot.class)
public abstract class CraftingResultSlotMixin {

    @Shadow private int removeCount;

    @Shadow private Player player;

    @Shadow private CraftingContainer craftSlots;

    @Inject(method = "checkTakeAchievements(Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"))
    private void nekojs$onCraftedTake(ItemStack carried, CallbackInfo ci) {
        if (removeCount > 0) {
            FabricPlayerEventBindings.postCrafted(player, carried, craftSlots);
        }
    }
}
