package com.tkisor.nekojs.wrapper.event.item;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * 物品使用完成事件（{@code ItemEvents.foodEaten}）的**加载器中立**载荷。
 *
 * <p>语义对齐 NeoForge {@code LivingEntityUseItemEvent.Finish}：物品的持续性使用
 * （吃食物、喝药水、拉弓、放烟花等）完成时触发，**不限定食物**——脚本可用
 * {@link #isFood()} 进一步过滤。fabric 侧由 {@code FabricItemEventBindingsV2} +
 * {@code MixinLivingEntityUseItemFinish} 从 {@code LivingEntity#completeUsingItem}
 * 的 {@code ItemStack.finishUsingItem} 调用点转换（每次使用完成恰好触发一次）。
 *
 * <p><b>26.x 平台事实</b>：{@code ItemStack}/{@code Item} 均不暴露
 * {@code isEdible()/getFoodProperties()}（javap 实证），食物是纯数据组件
 * {@code DataComponents.FOOD}（类型 {@code net.minecraft.world.food.FoodProperties}）；
 * 因此本载荷以 {@link #isFood()} 提供等价判断。
 *
 * <p>不可取消（NeoForge Finish 亦不可取消）。
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
