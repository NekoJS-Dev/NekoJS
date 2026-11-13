package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.fabric.event.FabricPlayerEventBindings;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.OptionalInt;

/**
 * Fabric hook points for {@code PlayerEvents.containerOpened/containerClosed} (checked with javap
 * on minecraft-merged-deobf-26.1.2.jar; the {@code containerMenu}/{@code inventoryMenu} fields are
 * declared on {@code Player} and both are public).
 *
 * <p>Injection strategy: a handler of an INVOKE injection can only take parameters of the host
 * method (the prefix), and the {@code AbstractContainerMenu} that {@code openMenu} creates is a
 * local variable, so it is out of reach. All three open methods therefore use
 * {@code @At("RETURN")} and read {@code containerMenu} (every {@code putfield containerMenu}
 * happens before the RETURN: offset 109 in openMenu, 66 in openHorseInventory, 66 in
 * openNautilusInventory).
 *
 * <p>Guard: {@code containerMenu != inventoryMenu} skips the "creative menu failed / no menu" path,
 * where the menu falls back to inventoryMenu. openMenu also requires
 * {@code getReturnValue().isPresent()} so it only fires on the successful {@code OptionalInt.of}
 * path.
 *
 * <p>{@code doCloseContainer()} takes no arguments, so a HEAD injection is used. At that point
 * {@code containerMenu} is still the old menu being closed ({@code removed} and the putfield both
 * happen later). The {@code transferState} INVOKE point is not used because its CP owner is the
 * static type {@code InventoryMenu} while the method is declared on {@code AbstractContainerMenu},
 * and the mixin INVOKE scan counts 0 call sites when the owner differs from the declaring class.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerContainerMixin {

    @Inject(method = "openMenu(Lnet/minecraft/world/MenuProvider;)Ljava/util/OptionalInt;",
            at = @At("RETURN"))
    private void nekojs$onContainerOpened(CallbackInfoReturnable<OptionalInt> cir) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        AbstractContainerMenu menu = self.containerMenu;
        if (menu != null && menu != self.inventoryMenu && cir.getReturnValue().isPresent()) {
            FabricPlayerEventBindings.postContainerOpened(self, menu);
        }
    }

    @Inject(method = "openHorseInventory(Lnet/minecraft/world/entity/animal/equine/AbstractHorse;Lnet/minecraft/world/Container;)V",
            at = @At("RETURN"))
    private void nekojs$onHorseContainerOpened(CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        AbstractContainerMenu menu = self.containerMenu;
        if (menu != null && menu != self.inventoryMenu) {
            FabricPlayerEventBindings.postContainerOpened(self, menu);
        }
    }

    @Inject(method = "openNautilusInventory(Lnet/minecraft/world/entity/animal/nautilus/AbstractNautilus;Lnet/minecraft/world/Container;)V",
            at = @At("RETURN"))
    private void nekojs$onNautilusContainerOpened(CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        AbstractContainerMenu menu = self.containerMenu;
        if (menu != null && menu != self.inventoryMenu) {
            FabricPlayerEventBindings.postContainerOpened(self, menu);
        }
    }

    @Inject(method = "doCloseContainer",
            at = @At("HEAD"))
    private void nekojs$onContainerClosed(CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        AbstractContainerMenu menu = self.containerMenu;
        if (menu != null && menu != self.inventoryMenu) {
            FabricPlayerEventBindings.postContainerClosed(self, menu);
        }
    }
}
