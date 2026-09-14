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
 * {@code ItemEvents.foodEaten} 的 fabric 挂点：{@code LivingEntity#completeUsingItem}
 * 中 {@code ItemStack.finishUsingItem(Level, LivingEntity)} 的调用点（INVOKE）。
 *
 * <p>javap 实证（minecraft-merged-deobf-26.1.2.jar）：
 * <ul>
 *   <li>{@code LivingEntity} 上<b>没有</b> {@code finishUsingItem} 方法（26.x 起用
 *       {@code completeUsingItem}），实际完成点是 {@code protected void completeUsingItem()}
 *       的字节码 61-69：{@code ItemStack.finishUsingItem:(Lnet/minecraft/world/level/Level;
 *       Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;}
 *       ——完整的物品持续性使用（食物/药水/弓/烟花）完成时<b>恰好</b>调用一次。</li>
 *   <li>调用点前已通过三处守卫（字节码 0-17 客户端未使用直接 return、23-42 物品不一致
 *       release、43-50 空栈/未使用 return），此处 {@code this.useItem}（protected 字段）
 *       即被完成的物品栈。</li>
 *   <li>调用点处 {@code getUsedItemHand()} 仍指向使用手（{@code stopUsingItem} 在
 *       之后 87-88 才执行），payload 的 hand 可取。</li>
 * </ul>
 *
 * <p>26.x 事实：{@code ItemStack}/{@code Item} 已不暴露 {@code isEdible()}（javap 逐行
 * 实证零匹配），食物是数据组件 {@code net.minecraft.world.food.FoodProperties}
 * （{@code DataComponents.FOOD}）；payload 以 {@code event.isFood()} 提供等价判断。
 * 语义对齐 NeoForge {@code LivingEntityUseItemEvent.Finish}：所有物品使用完成都触发
 * （不限定食物），脚本用 {@code event.isFood()} 过滤。客户端实例按 SERVER 总线约定过滤
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityUseItemFinish {

    @Shadow
    protected ItemStack useItem;

    // TAIL 注入：completeUsingItem 返回 void，其方法体在 finishUsingItem 调用点后即完成
    // （62-70 字节码），TAIL 与 INVOKE 点语义等价且不受 INVOKE 目标匹配影响（defaultRequire=1
    // 下的更稳形态）。INVOKE 匹配失败的根因未查明（其他 mixin 同款 target 匹配正常）——
    // 待后续核实后再改回精确点。
    @Inject(method = "completeUsingItem", at = @At("TAIL"))
    private void nekojs$onCompleteUsingItem(CallbackInfo ci) {
        if (((LivingEntity) (Object) this).level().isClientSide()) {
            return;
        }
        LivingEntity nekojs$entity = (LivingEntity) (Object) this;
        FabricItemEventBindingsV2.postFoodEaten(nekojs$entity, nekojs$entity.getUseItem(), nekojs$entity.getUsedItemHand());
    }
}
