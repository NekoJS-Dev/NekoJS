package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.fabric.event.FabricItemEventBindings;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 服务端 UseItem 包处理钩子：{@code ItemEvents.rightClicked} 的 fabric 服务端入口。
 * fabric-api 的 UseItemCallback 是纯客户端事件，喂不了 SERVER 总线；NeoForge 的
 * RightClickItem 在服务端实例投递（EventBusForgeBridge 的 side filter），此处对齐。
 * 脚本取消（return true）时跳过原版使用处理。
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerMixin {

    @Shadow public ServerPlayer player;

    @Inject(method = "handleUseItem", at = @At("HEAD"), cancellable = true)
    private void nekojs$onHandleUseItem(ServerboundUseItemPacket packet, CallbackInfo ci) {
        if (FabricItemEventBindings.postRightClicked(player, packet.getHand())) {
            ci.cancel();
        }
    }
}
