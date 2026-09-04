package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.fabric.event.FabricItemEventBindingsV2;
import com.tkisor.nekojs.fabric.event.FabricPlayerEventBindings;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code ItemEvents.entityInteracted} 的 fabric 挂点：
 * {@code ServerGamePacketListenerImpl#handleInteract(ServerboundInteractPacket)} 中
 * {@code ServerPlayer.interactOn} 的调用点（INVOKE），按交互时<b>主手物品</b> dispatch。
 *
 * <p>javap 实证（minecraft-merged-deobf-26.1.2.jar）：
 * <ul>
 *   <li>{@code handleInteract} 是 public 具体方法；{@code ServerboundInteractPacket} 是
 *       record（{@code entityId()/hand()/location()/usingSecondaryAction()}）；
 *       攻击走独立的 {@code handleAttack(ServerboundAttackPacket)}（3894 行），本挂在
 *       交互侧不会拦截攻击。</li>
 *   <li>调用点顺序（字节码）：{@code level.getEntityOrPart(entityId())}（55-73 实体为 null
 *       或出世界边界 return）→ 交互范围校验（80-95）→ {@code packet.hand()} →
 *       {@code player.getItemInHand(hand)}（119-131 未启用特性 return）→
 *       {@code player.interactOn(entity, hand, location)}（139-148）。选 INVOKE 点在
 *       上述校验全部通过之后，实体/手/物品均安全。</li>
 *   <li>{@code Player.interactOn} 的 descriptor：
 *       {@code (Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/InteractionHand;
 *       Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/InteractionResult;}。</li>
 * </ul>
 *
 * <p>可取消：监听器 {@code return true} → {@code ci.cancel()} 取消整包处理（原版交互不执行），
 * 对应 NeoForge {@code PlayerInteractEvent.EntityInteract} 的 setCanceled。
 * <b>命名注意</b>：目标类已有 {@code ServerGamePacketListenerMixin}（handleUseItem/rightClicked，
 * 不同方法），本类以 ItemInteract 后缀独立命名防冲突。
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MixinServerGamePacketListenerItemInteract {

    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleInteract(Lnet/minecraft/network/protocol/game/ServerboundInteractPacket;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;interactOn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/InteractionResult;"),
            cancellable = true)
    private void nekojs$onHandleInteract(ServerboundInteractPacket packet, CallbackInfo ci) {
        Entity target = this.player.level().getEntityOrPart(packet.entityId());
        if (target == null) {
            return;
        }
        InteractionHand hand = packet.hand();
        // PlayerEvents.entityInteract 与本事件的注入点完全相同（handleInteract →
        // player.interactOn 调用点，目标实体已存在/在界内/在交互距离内）——曾以独立
        // mixin 在相同点注入，但那是一个扫描 0 的 mixin 怪癖（注解与字节码与本文
        // 完全一致的孪生注入却通过），故双事件合并到本 handler 一次 dispatch。
        if (FabricPlayerEventBindings.postEntityInteract(this.player, target, hand)) {
            ci.cancel();
        }
        ItemStack stack = this.player.getItemInHand(hand);
        if (FabricItemEventBindingsV2.postEntityInteracted(this.player, target, stack, hand)) {
            ci.cancel();
        }
    }
}
