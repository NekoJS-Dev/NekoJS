package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.fabric.event.FabricItemEventBindingsV2;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabric hook for {@code ItemEvents.canPickUp}, {@code ItemEvents.pickedUpPre} and
 * {@code ItemEvents.pickedUp}: {@code ItemEntity#playerTouch(Player)}.
 *
 * <p>Checked with javap (minecraft-merged-deobf-26.1.2.jar):
 * <ul>
 *   <li>{@code public void playerTouch(net.minecraft.world.entity.player.Player)} is a concrete
 *       method, so HEAD can be injected. The first branch of the bytecode is
 *       {@code level().isClientSide() -> return}, so the server guard is already there.</li>
 *   <li>Members available at the entry: {@code this} (the ItemEntity, with {@code getItem()} for
 *       the stack) and the {@code player} parameter. The success chain is
 *       {@code Inventory.add -> Player.take -> discard -> awardStat -> Player.onItemPickup(this)};
 *       {@code onItemPickup} is the only and last call site on that path (bytecode 103-105), and
 *       no early-return branch passes through it.</li>
 * </ul>
 *
 * <p>Pre is injected at HEAD and is cancellable: a listener returning true leads to
 * {@code ci.cancel()}, which skips the whole pickup and matches cancelling
 * {@code ItemEntityPickupEvent.Pre} on NeoForge. When the pickup is cancelled the
 * {@code onItemPickup} injection is naturally not reached, so no post event fires twice. Post is
 * injected at the {@code onItemPickup} INVOKE point, which is already after the pickup (the item
 * is in the inventory and the statistic is counted). Both filter clients with
 * {@code level().isClientSide()}, because the SERVER bus only receives server-side instances.
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

    // TAIL plus the isRemoved() test: the success chain always ends with discard() inside playerTouch
    // (javap offset 81) and no early-return branch passes through it. The TAIL injection runs before
    // every exit of playerTouch, so on the cancel and failure paths the entity is still in the level
    // (isRemoved() == false) and on the success path it has been removed (== true), and Post is
    // delivered only on success. (The onItemPickup INVOKE point has owner Player, the compile-time
    // reference class, while the method is declared on LivingEntity, so sponge 0.17.3 found no match;
    // this uses the condition-based TAIL instead.)
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
