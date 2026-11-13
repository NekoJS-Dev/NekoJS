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
 * The Fabric hook for {@code ItemEvents.dropped}: it targets
 * {@code ServerPlayer#drop(ItemStack, boolean, boolean)}, the three-argument overload in 26.1.2.
 * It is not the two-argument overload that Player had in the NeoForge era; in 26.1.2
 * {@code Player#drop(ItemStack, boolean)} is a concrete method that forwards straight to the
 * three-argument one.
 *
 * <p>Checked with javap against minecraft-merged-deobf-26.1.2.jar:
 * <ul>
 *   <li>The concrete body of {@code Player#drop(ItemStack, boolean)} is
 *       {@code return this.drop(stack, false, boolean)}; dropping an item out of the inventory GUI
 *       goes through it.</li>
 *   <li>{@code ServerPlayer#drop(boolean)} (the Q key or creative slot drop) =
 *       {@code Inventory.removeFromSelected
 *       → this.drop(stack, false, true)}; it also lands in the three-argument overload.</li>
 *   <li>On {@code ServerPlayer} the three-argument overload is a public concrete method that
 *       overrides {@code LivingEntity#drop}. It is the one place both player-initiated drop paths
 *       meet.</li>
 *   <li>Death drops are excluded: {@code die()} sets {@code dead=true} before
 *       {@code dropAllDeathLoot} (bytecode 91-93 vs 150), and
 *       {@code isDeadOrDying() = getHealth() <= 0}. The three-argument overload is on the death
 *       equipment and inventory drop path ({@code Inventory.dropAll} →
 *       {@code player.drop(stack, true, false)}), so the handler filters on
 *       {@code isDeadOrDying()}.</li>
 * </ul>
 *
 * <p>{@code ServerPlayer} is always a logical server instance, so no client filter is needed. The
 * bus is notification-only: Fabric cannot cancel, and cancel semantics live in the
 * {@code ItemDroppedEventJS} javadoc.
 */
@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayerItemDrop {

    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("HEAD"))
    private void nekojs$onDrop(ItemStack stack, boolean spread, boolean retainOwnership,
                               CallbackInfoReturnable<ItemEntity> cir) {
        if (((ServerPlayer) (Object) this).isDeadOrDying()) {
            // Death drops (equipment and inventory) are not part of NeoForge ItemTossEvent semantics
            return;
        }
        FabricItemEventBindingsV2.postDropped((ServerPlayer) (Object) this, stack);
    }
}
