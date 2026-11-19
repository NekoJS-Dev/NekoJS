package com.tkisor.nekojs.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tkisor.nekojs.fabric.event.FabricItemEventBindingsV2;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
public abstract class MixinPlayerItemToss {
    @WrapOperation(method = "drop(Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;"))
    private ItemEntity nekojs$inventoryToss(Player player, ItemStack stack, boolean spread, boolean retainOwnership,
                                              Operation<ItemEntity> original) {
        if (player instanceof ServerPlayer) {
            return FabricItemEventBindingsV2.withPlayerToss(player, stack,
                    () -> original.call(player, stack, spread, retainOwnership));
        }
        return original.call(player, stack, spread, retainOwnership);
    }
}
