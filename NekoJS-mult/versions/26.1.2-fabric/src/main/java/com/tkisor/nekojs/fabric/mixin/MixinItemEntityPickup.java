package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.fabric.event.FabricItemEventBindingsV2;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code ItemEvents.canPickUp / pickedUpPre / pickedUp} 的 fabric 挂点：
 * {@code ItemEntity#playerTouch(Player)}。
 *
 * <p>javap 实证（minecraft-merged-deobf-26.1.2.jar）：
 * <ul>
 *   <li>{@code public void playerTouch(net.minecraft.world.entity.player.Player)}——具体方法，HEAD 可注入；
 *       方法字节码首个分支是 {@code level().isClientSide() → return}（服务端守卫原生存在）。</li>
 *   <li>入口处可拿成员：{@code this}（ItemEntity，{@code getItem()} 取物品栈）、参数 {@code player}；
 *       拾取成功链 = {@code Inventory.add → Player.take → discard → awardStat → Player.onItemPickup(this)}，
 *       {@code onItemPickup} 是成功路径上唯一且最后的调用点（字节码 103-105，早退分支均不经过）。</li>
 * </ul>
 *
 * <p>Pre 在 HEAD 注入（可取消：监听器 return true → {@code ci.cancel()} 跳过整个拾取流程，
 * 对应 NeoForge {@code ItemEntityPickupEvent.Pre} 的取消；本类另在取消情形下保证
 * {@code onItemPickup} 调用点注入自然不经过，无重复）。Post 在 {@code onItemPickup}
 * 调用点（INVOKE）注入——该点已在拾取完成后（物品进背包、统计已加）。
 * 双方都以 {@code level().isClientSide()} 过滤客户端（SERVER 总线只收服务端实例）。
 */
@Mixin(ItemEntity.class)
public abstract class MixinItemEntityPickup {

    @Inject(method = "playerTouch(Lnet/minecraft/world/entity/player/Player;)V", at = @At("HEAD"), cancellable = true)
    private void nekojs$onPlayerTouchPre(Player player, CallbackInfo ci) {
        if (((ItemEntity) (Object) this).level().isClientSide()) {
            return;
        }
        if (FabricItemEventBindingsV2.postCanPickUp(player, (ItemEntity) (Object) this)) {
            ci.cancel();
        }
    }

    // TAIL + isRemoved() 判据：成功链的确定性收尾是 playerTouch 内 discard()（javap 偏移 81，
    // 早退分支均不经过）；TAIL 注入在 playerTouch 所有出口前执行，取消/失败路径实体仍在
    // level（isRemoved()==false），成功路径已移除（==true）——Post 只在成功时投递。
    // （INVOKE 点 onItemPickup 的 owner 是编译期引用类 Player 而声明在 LivingEntity，
    // 该项 sponge 0.17.3 匹配扫 0，改用判据式 TAIL。）
    @Inject(method = "playerTouch(Lnet/minecraft/world/entity/player/Player;)V", at = @At("TAIL"))
    private void nekojs$onPlayerTouchPost(Player player, CallbackInfo ci) {
        if (((ItemEntity) (Object) this).level().isClientSide()) {
            return;
        }
        if (!((ItemEntity) (Object) this).isRemoved()) {
            return;
        }
        FabricItemEventBindingsV2.postPickedUp(player, (ItemEntity) (Object) this);
    }
}
