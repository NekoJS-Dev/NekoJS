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
 * Fabric hook for the player crafting event ({@code PlayerEvents.crafted}, dispatched by result
 * item id).
 *
 * <p>On NeoForge 26.1.2 the event fires inside {@code ResultSlot#checkTakeAchievements}, in the
 * {@code if (removeCount > 0)} block after {@code carried.onCraftedBy(...)} (the userdev patch
 * calls firePlayerCraftingEvent(player, carried, craftSlots)). This mixin uses the same place.
 *
 * <p>Injection point (checked with javap on minecraft-merged-deobf-26.1.2):
 * {@code checkTakeAchievements} is a protected concrete method, so a TAIL injection is not lost
 * the way it is on an abstract method. Its body starts with an ifle guard on removeCount at
 * offset 4. HEAD therefore runs before onCraftedBy and the rewards, just before the NeoForge
 * point, with the same payload members. The shared ResultSlotMixin
 * (com.tkisor.nekojs.mixin) injects into {@code getRemainingItems}, a different class and method,
 * so the two do not conflict.
 *
 * <p>The guard is the same {@code removeCount > 0} as on NeoForge: onQuickCraft and onTake both
 * add to removeCount before entering this method, so an empty take (removeCount == 0) fires no
 * event. defaultRequire=1 makes a refactored method fail hard.
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
