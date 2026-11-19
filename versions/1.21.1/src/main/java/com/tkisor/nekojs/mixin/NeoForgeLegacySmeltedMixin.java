package com.tkisor.nekojs.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.tkisor.nekojs.bindings.event.NeoForgeSmeltingRouting;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.EventHooks;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FurnaceResultSlot.class)
public abstract class NeoForgeLegacySmeltedMixin {
    @Shadow private int removeCount;

    @Inject(method = "checkTakeAchievements(Lnet/minecraft/world/item/ItemStack;)V",
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/inventory/FurnaceResultSlot;removeCount:I",
                    opcode = Opcodes.PUTFIELD), require = 1, allow = 1)
    private void nekojs$captureEmptyTake(CallbackInfo ci, @Share("emptyFurnaceTake") LocalBooleanRef emptyTake) {
        emptyTake.set(removeCount == 0);
    }

    @WrapOperation(method = "checkTakeAchievements(Lnet/minecraft/world/item/ItemStack;)V",
            at = @At(value = "INVOKE", remap = false,
                    target = "Lnet/neoforged/neoforge/event/EventHooks;firePlayerSmeltedEvent(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V"),
            require = 1, allow = 1)
    private void nekojs$publishSmelted(Player player, ItemStack stack, Operation<Void> original,
                                        @Share("emptyFurnaceTake") LocalBooleanRef emptyTake) {
        if (emptyTake.get()) NeoForgeSmeltingRouting.duringEmptyTake(() -> original.call(player, stack));
        else original.call(player, stack);
    }

    @Mixin(value = EventHooks.class, remap = false)
    public abstract static class EventDispatch {
        @WrapOperation(method = "firePlayerSmeltedEvent", at = @At(value = "INVOKE",
                target = "Lnet/neoforged/bus/api/IEventBus;post(Lnet/neoforged/bus/api/Event;)Lnet/neoforged/bus/api/Event;"),
                require = 1, allow = 1)
        private static Event nekojs$dispatchLegacySmelted(IEventBus bus, Event event, Operation<Event> original) {
            return NeoForgeSmeltingRouting.postLegacyEvent(event, () -> original.call(bus, event));
        }
    }
}
