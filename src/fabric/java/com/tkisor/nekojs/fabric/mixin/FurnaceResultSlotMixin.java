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
 * Fabric hook for the player smelting event ({@code PlayerEvents.smelted}, dispatched by result
 * item id).
 *
 * <p>On NeoForge 26.1.2 the event fires inside {@code FurnaceResultSlot#checkTakeAchievements},
 * in {@code if (removeCount != 0) { ... awardUsedRecipesAndPopExperience ...
 * firePlayerSmeltedEvent(player, carried, removeCount) }} (from the userdev patch). The reward
 * point in {@code ServerPlayerRecipes} is not the right place: in 26.1.2 that vanilla class is
 * now {@code net.minecraft.stats.ServerRecipeBook}, whose addRecipes runs from
 * {@code ServerPlayer#awardRecipes} to store recipes in the recipe book, not to take smelted
 * items, so it would fire at the wrong time. This mixin uses the same point as NeoForge, so it
 * fires for furnace, blast furnace and smoker takes; a campfire has no result slot and does not
 * fire, the same as on NeoForge.
 *
 * <p>Injection point (checked with javap on minecraft-merged-deobf-26.1.2):
 * {@code checkTakeAchievements} is a protected concrete method. Its body runs
 * {@code carried.onCraftedBy(player, removeCount)} and {@code awardUsedRecipesAndPopExperience},
 * then resets {@code removeCount = 0}. HEAD runs under the same {@code removeCount != 0}
 * condition as NeoForge; at that point removeCount is already accumulated and onCraftedBy has
 * not run yet. defaultRequire=1 makes a refactored method fail hard.
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
