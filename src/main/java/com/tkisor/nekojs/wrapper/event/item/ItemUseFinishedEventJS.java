package com.tkisor.nekojs.wrapper.event.item;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Loader-neutral payload for the item use finished event ({@code ItemEvents.foodEaten}).
 *
 * <p>The meaning follows NeoForge {@code LivingEntityUseItemEvent.Finish}: it fires when the
 * continuous use of an item finishes (eating food, drinking a potion, shooting a bow, using
 * fireworks, ...) and is <b>not</b> limited to food, so a script can filter further with
 * {@link #isFood()}. On fabric, {@code FabricItemEventBindingsV2} +
 * {@code MixinLivingEntityUseItemFinish} convert the {@code ItemStack.finishUsingItem} call
 * site in {@code LivingEntity#completeUsingItem}, which fires exactly once per finished use.
 *
 * <p><b>26.x platform fact</b>: neither {@code ItemStack} nor {@code Item} exposes
 * {@code isEdible()/getFoodProperties()} (javap-verified); food is the pure data component
 * {@code DataComponents.FOOD} of type {@code net.minecraft.world.food.FoodProperties}. This
 * payload therefore provides the equivalent check as {@link #isFood()}.
 *
 * <p>Not cancellable (NeoForge Finish is not cancellable either).
 */
@Doc("Fired when a living entity finishes using an item (food eaten, potion drunk, bow shot, ...) — ItemEvents.foodEaten.")
@Doc("Fires for any item-use completion; use event.isFood() to filter to foods. Not cancellable.")
@Getter
public class ItemUseFinishedEventJS {

    @Doc("The living entity that used the item.")
    private final LivingEntity entity;

    @Doc("The item stack that was consumed / used.")
    private final ItemStack item;

    @Doc("The hand the item was used from (as returned by LivingEntity.getUsedItemHand()).")
    private final InteractionHand hand;

    public ItemUseFinishedEventJS(LivingEntity entity, ItemStack item, InteractionHand hand) {
        this.entity = entity;
        this.item = item;
        this.hand = hand;
    }

    @Doc("Whether the used item is food (has the FOOD data component, 26.x equivalent of isEdible()).")
    public boolean isFood() {
        return item.get(DataComponents.FOOD) != null;
    }
}
