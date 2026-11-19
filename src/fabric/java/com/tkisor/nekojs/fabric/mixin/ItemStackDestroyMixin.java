package com.tkisor.nekojs.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.tkisor.nekojs.bindings.event.PlayerEvents;
import com.tkisor.nekojs.fabric.event.FabricPlayerEventBindings;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
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
 *   <li>HEAD records {@code isBroken()} and a copy of the stack before damage shrinks it to
 *       {@code EMPTY}; the durability reduction in {@code processDurabilityChange} does not
 *       change this test.</li>
 *   <li>RETURN compares against that snapshot: "not broken on entry and broken/empty at the end"
 *       means this call broke the item, so the destroyed event fires with the non-empty copy.</li>
 * </ul>
 * INVOKE was not used because scanning for shrink, onItemPickup and finishUsingItem found no
 * targets, while initMenu and interactOn matched. The cause of those failures is unknown, and
 * with {@code defaultRequire=1} a miss cannot be skipped silently, so the condition form is
 * more reliable here.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackDestroyMixin {

    /** Snapshot of {@code isBroken()} taken at the entry of applyDamage (main thread, no reentry, safe). */


    /** Non-empty copy of the ItemStack captured before applyDamage shrinks it to EMPTY. */


    @Inject(method = "applyDamage(ILnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V", at = @At("HEAD"))
    private void nekojs$snapshotBroken(int amount, ServerPlayer player, Consumer<Item> onBroken, CallbackInfo ci,
                                       @Share("beforeDamage") LocalRef<ItemStack> beforeDamage,
                                       @Share("equipmentDamage") LocalBooleanRef equipmentDamage) {
        ItemStack self = (ItemStack) (Object) this;
        if (player == null || !PlayerEvents.DESTROYED.hasListeners()) return;
        equipmentDamage.set(FabricPlayerEventBindings.takeEquipmentDamage(player, self));
        if (!self.isBroken()) beforeDamage.set(self.copy());
    }

    @Inject(method = "applyDamage(ILnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V", at = @At("RETURN"))
    private void nekojs$onItemBroken(int amount, ServerPlayer player, Consumer<Item> onBroken, CallbackInfo ci,
                                     @Share("beforeDamage") LocalRef<ItemStack> beforeDamage,
                                     @Share("equipmentDamage") LocalBooleanRef equipmentDamage) {
        ItemStack snapshot = beforeDamage.get();
        if (snapshot == null || player == null) {
            return;
        }
        ItemStack self = (ItemStack) (Object) this;
        if (self.isBroken() || self.isEmpty()) {
            FabricPlayerEventBindings.postDestroyed(player, self, snapshot, equipmentDamage.get());
        }
    }

    @WrapOperation(method = "useOn(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/Item;useOn(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;"),
            require = 1, allow = 1)
    private InteractionResult nekojs$captureUseOn(Item item, UseOnContext context, Operation<InteractionResult> original) {
        if (!(context.getPlayer() instanceof ServerPlayer player)) return original.call(item, context);
        return FabricPlayerEventBindings.captureUseOnDestruction(player, (ItemStack) (Object) this,
                () -> original.call(item, context));
    }
}
