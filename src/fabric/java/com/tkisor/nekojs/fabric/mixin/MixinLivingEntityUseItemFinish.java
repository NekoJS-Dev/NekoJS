package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.fabric.event.FabricItemEventBindingsV2;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabric hook for {@code ItemEvents.foodEaten}: the call site of
 * {@code ItemStack.finishUsingItem(Level, LivingEntity)} in {@code LivingEntity#completeUsingItem}
 * (INVOKE).
 *
 * <p>Checked with javap (minecraft-merged-deobf-26.1.2.jar):
 * <ul>
 *   <li>{@code LivingEntity} has <b>no</b> {@code finishUsingItem} method; since 26.x the
 *       finishing point is {@code protected void completeUsingItem()}, and its bytecode 61-69
 *       calls {@code ItemStack.finishUsingItem:(Lnet/minecraft/world/level/Level;
 *       Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;}, exactly
 *       once when a continuous item use (food, potion, bow, firework) completes.</li>
 *   <li>Three guards pass before the call site (bytecode 0-17 returns when the client is not
 *       using an item, 23-42 releases on a mismatched item, 43-50 returns on an empty or unused
 *       stack), so here {@code this.useItem} (a protected field) is the stack that finished.</li>
 *   <li>At the call site {@code getUsedItemHand()} still points at the using hand, because
 *       {@code stopUsingItem} only runs afterwards at 87-88, so the payload can read the hand.</li>
 * </ul>
 *
 * <p>26.x fact: {@code ItemStack} and {@code Item} no longer expose {@code isEdible()} (javap
 * matches nothing line by line); food is the data component
 * {@code net.minecraft.world.food.FoodProperties} ({@code DataComponents.FOOD}), and the payload
 * offers the equivalent check as {@code event.isFood()}. Semantics match NeoForge
 * {@code LivingEntityUseItemEvent.Finish}: every completed item use fires, not only food, and
 * scripts filter with {@code event.isFood()}. Client instances are filtered by the SERVER bus
 * rule.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityUseItemFinish {

    @Shadow
    protected ItemStack useItem;

    // TAIL injection: completeUsingItem returns void and its body ends right after the finishUsingItem
    // call (bytecode 62-70), so TAIL is equivalent to the INVOKE point and does not depend on INVOKE
    // target matching, which is the safer form under defaultRequire=1. Why the INVOKE match failed is
    // unknown (other mixins match the same target), so switch back to the exact point once the cause
    // is found.
    @Inject(method = "completeUsingItem", at = @At("TAIL"))
    private void nekojs$onCompleteUsingItem(CallbackInfo ci) {
        if (((LivingEntity) (Object) this).level().isClientSide()) {
            return;
        }
        LivingEntity nekojs$entity = (LivingEntity) (Object) this;
        FabricItemEventBindingsV2.postFoodEaten(nekojs$entity, nekojs$entity.getUseItem(), nekojs$entity.getUsedItemHand());
    }
}
