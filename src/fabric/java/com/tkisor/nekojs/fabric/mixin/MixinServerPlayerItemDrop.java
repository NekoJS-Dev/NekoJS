package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.fabric.event.FabricItemEventBindingsV2;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code ItemEvents.dropped} 的 fabric 挂点：
 * {@code ServerPlayer#drop(ItemStack, boolean, boolean)}（26.1.2 的三参重载，
 * 不是 NeoForge 时代 Player 上的两参重载——两参 {@code Player#drop(ItemStack, boolean)}
 * 在 26.1.2 是具体方法且内部直转三参）。
 *
 * <p>javap 实证（minecraft-merged-deobf-26.1.2.jar）：
 * <ul>
 *   <li>{@code Player#drop(ItemStack, boolean)} 具体实现 = {@code return this.drop(stack, false, boolean)}
 *       ——拖出物品栏 GUI 的丢弃走它。</li>
 *   <li>{@code ServerPlayer#drop(boolean)}（Q 键/创造栏槽丢弃）= {@code Inventory.removeFromSelected
 *       → this.drop(stack, false, true)}——同样落到三参重载。</li>
 *   <li>三参重载在 {@code ServerPlayer} 上是 public 具体方法（覆写了
 *       {@code LivingEntity#drop}），是两条玩家主动丢弃路径的唯一汇聚点。</li>
 *   <li>死亡掉落排除：{@code die()} 在 {@code dropAllDeathLoot} 之前 {@code dead=true}
 *       （字节码 91-93 vs 150），且 {@code isDeadOrDying() = getHealth() <= 0}——
 *       三参重载是死亡装备/背包掉落的必经点（{@code Inventory.dropAll} →
 *       {@code player.drop(stack, true, false)}），以 {@code isDeadOrDying()} 过滤。</li>
 * </ul>
 *
 * <p>注意：{@code ServerPlayer} 恒为逻辑服务端实例，无需客户端过滤。
 * 通知型总线（fabric 不支持取消——取消语义见 {@code ItemDroppedEventJS} javadoc）。
 */
@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayerItemDrop {

    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("HEAD"))
    private void nekojs$onDrop(ItemStack stack, boolean spread, boolean retainOwnership,
                               CallbackInfoReturnable<ItemEntity> cir) {
        if (((ServerPlayer) (Object) this).isDeadOrDying()) {
            // 死亡掉落（装备/背包）不属于 NeoForge ItemTossEvent 语义
            return;
        }
        FabricItemEventBindingsV2.postDropped((ServerPlayer) (Object) this, stack);
    }
}
