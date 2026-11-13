package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.fabric.event.FabricPlayerEventBindings;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * Fabric hook for the player item-destroyed event ({@code PlayerEvents.destroyed}, dispatched by
 * item id).
 *
 * <p>On 26.1.2 {@code ItemStack#applyDamage(int, ServerPlayer, Consumer)} is the single place
 * that decides breaking: setDamageValue, then the isBroken branch, then shrink, then
 * onBroken.accept. Every NeoForge 26.1.2 onPlayerDestroyItem call site (block breaking,
 * attacking, item use, shield blocking, fishing rod) goes through it, so hooking this one point
 * on fabric covers them all. Non-player entities such as a zombie's tool are filtered out by
 * {@code player == null}.
 *
 * <p>The injection is condition based and has no INVOKE target:
 * <ul>
 *   <li>HEAD records {@code isBroken()} as the instance is entered, before damage is updated;
 *       the durability reduction in {@code processDurabilityChange} does not change this test.</li>
 *   <li>RETURN compares against that snapshot: "not broken on entry and broken at the end" means
 *       this call broke the item, so the destroyed event fires. This is equivalent to the
 *       isBroken branch and tells a reused item that is already broken apart from one that
 *       breaks during the call.</li>
 * </ul>
 * INVOKE was not used because scanning for shrink, onItemPickup and finishUsingItem found no
 * targets, while initMenu and interactOn matched. The cause of those failures is unknown, and
 * with {@code defaultRequire=1} a miss cannot be skipped silently, so the condition form is
 * more reliable here.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackDestroyMixin {

    /** Snapshot of {@code isBroken()} taken at the entry of applyDamage (main thread, no reentry, safe). */
    @Unique
    private boolean neko$brokenAtEntry;

    @Inject(method = "applyDamage(ILnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V", at = @At("HEAD"))
    private void nekojs$snapshotBroken(int amount, ServerPlayer player, Consumer<Item> onBroken, CallbackInfo ci) {
        neko$brokenAtEntry = ((ItemStack) (Object) this).isBroken();
    }

    @Inject(method = "applyDamage(ILnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V", at = @At("RETURN"))
    private void nekojs$onItemBroken(int amount, ServerPlayer player, Consumer<Item> onBroken, CallbackInfo ci) {
        boolean brokenNow = ((ItemStack) (Object) this).isBroken();
        if (!neko$brokenAtEntry && brokenNow && player != null) {
            FabricPlayerEventBindings.postDestroyed(player, ((ItemStack) (Object) this).copy());
        }
    }
}
