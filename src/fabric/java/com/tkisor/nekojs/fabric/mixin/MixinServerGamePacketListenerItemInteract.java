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
 * Fabric hook for {@code ItemEvents.entityInteracted}: the
 * {@code ServerPlayer.interactOn} call site in
 * {@code ServerGamePacketListenerImpl#handleInteract(ServerboundInteractPacket)} (INVOKE),
 * dispatched by the <b>main hand item</b> used for the interaction.
 *
 * <p>Checked with javap (minecraft-merged-deobf-26.1.2.jar):
 * <ul>
 *   <li>{@code handleInteract} is a public concrete method. {@code ServerboundInteractPacket} is a
 *       record ({@code entityId()/hand()/location()/usingSecondaryAction()}). Attacks go through a
 *       separate {@code handleAttack(ServerboundAttackPacket)}, so this interaction hook never
 *       intercepts attacks.</li>
 *   <li>Call order in the bytecode: {@code level.getEntityOrPart(entityId())} (55-73, returns when
 *       the entity is null or outside the world), the interaction range check (80-95),
 *       {@code packet.hand()}, {@code player.getItemInHand(hand)} (119-131, returns when the
 *       feature is disabled), then {@code player.interactOn(entity, hand, location)} (139-148).
 *       The INVOKE point comes after all these checks, so the entity, hand and item are all
 *       valid.</li>
 *   <li>The descriptor of {@code Player.interactOn} is
 *       {@code (Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/InteractionHand;
 *       Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/InteractionResult;}.</li>
 * </ul>
 *
 * <p>Cancellable: a listener returning {@code true} leads to {@code ci.cancel()}, which cancels
 * handling of the whole packet so the vanilla interaction does not run; this matches setCanceled
 * on NeoForge {@code PlayerInteractEvent.EntityInteract}. <b>Naming</b>: the target class already
 * has {@code ServerGamePacketListenerMixin} (handleUseItem/rightClicked, different methods), so
 * this class takes the ItemInteract suffix to avoid a name clash.
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
        // PlayerEvents.entityInteract uses exactly the same injection point as this event: handleInteract ->
        // the player.interactOn call site, after the target entity exists, is inside the world and is
        // within interaction distance. It once had its own mixin at that point, but that hit a mixin quirk
        // where the scan found nothing even though a twin injection with identical annotations and
        // bytecode passed, so both events are dispatched once from this handler.
        if (FabricPlayerEventBindings.postEntityInteract(this.player, target, hand)) {
            ci.cancel();
        }
        ItemStack stack = this.player.getItemInHand(hand);
        if (FabricItemEventBindingsV2.postEntityInteracted(this.player, target, stack, hand)) {
            ci.cancel();
        }
    }
}
