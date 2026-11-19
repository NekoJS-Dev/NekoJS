package com.tkisor.nekojs.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tkisor.nekojs.bindings.event.EntityEvents;
import com.tkisor.nekojs.bindings.event.ItemEvents;
import com.tkisor.nekojs.fabric.event.FabricEntityEventBindingsV2;
import com.tkisor.nekojs.fabric.event.FabricItemEventBindingsV2;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Fabric hook for {@code ItemEvents.foodEaten} at the entry of
 * {@code LivingEntity#completeUsingItem()}.
 *
 * <p>26.x fact: {@code ItemStack} and {@code Item} no longer expose {@code isEdible()}; food is
 * the data component {@code net.minecraft.world.food.FoodProperties} ({@code DataComponents.FOOD}),
 * and the payload offers the equivalent check as {@code event.isFood()}. Semantics match NeoForge
 * {@code LivingEntityUseItemEvent.Finish}: every completed item use fires, not only food, and
 * scripts filter with {@code event.isFood()}. Client instances are filtered by the SERVER bus
 * rule.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityUseItemFinish {

    // HEAD injection: stopUsingItem() runs at the end of completeUsingItem() and clears
    // this.useItem to ItemStack.EMPTY, so reading getUseItem() and getUsedItemHand() at HEAD
    // captures the active stack and hand before vanilla resets them.
    @WrapOperation(method = "completeUsingItem()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;finishUsingItem(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack nekojs$onCompleteUsingItem(ItemStack stack, Level level, LivingEntity entity,
                                                  Operation<ItemStack> original) {
        if (level.isClientSide() || (!EntityEvents.USE_FINISHED.hasListeners() && !ItemEvents.FOOD_EATEN.hasListeners())) {
            return original.call(stack, level, entity);
        }
        ItemStack used = stack.copy();
        int remainingTicks = entity.getUseItemRemainingTicks();
        InteractionHand hand = entity.getUsedItemHand();
        ItemStack result = original.call(stack, level, entity);
        FabricEntityEventBindingsV2.postUseItemFinished(entity, used, remainingTicks);
        FabricItemEventBindingsV2.postFoodEaten(entity, used, hand);
        return result;
    }
}
