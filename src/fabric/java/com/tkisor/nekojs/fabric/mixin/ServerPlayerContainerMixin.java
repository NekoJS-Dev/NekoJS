package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.fabric.event.FabricPlayerEventBindings;
import com.tkisor.nekojs.stage.StageGateEnforcer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.OptionalInt;

/**
 * Fabric hook points for container stage gate enforcement and
 * {@code PlayerEvents.containerOpened/containerClosed}.
 *
 * <p>All three open methods inject at {@code @At("RETURN")} and read {@code containerMenu}. When
 * {@link StageGateEnforcer#allowOpenContainer} denies opening the menu, {@code closeContainer()}
 * closes it immediately without dispatching {@code containerOpened} or a spurious
 * {@code containerClosed} event.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerContainerMixin {

    @Unique
    private boolean nekojs$closingBlockedContainer;

    @Inject(method = "openMenu(Lnet/minecraft/world/MenuProvider;)Ljava/util/OptionalInt;",
            at = @At("RETURN"))
    private void nekojs$onContainerOpened(CallbackInfoReturnable<OptionalInt> cir) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        AbstractContainerMenu menu = self.containerMenu;
        if (menu != null && menu != self.inventoryMenu && cir.getReturnValue().isPresent()) {
            if (!StageGateEnforcer.allowOpenContainer(self, menu)) {
                nekojs$closeBlockedContainer(self);
                return;
            }
            FabricPlayerEventBindings.postContainerOpened(self, menu);
        }
    }

    @Inject(method = "openHorseInventory(Lnet/minecraft/world/entity/animal/equine/AbstractHorse;Lnet/minecraft/world/Container;)V",
            at = @At("RETURN"))
    private void nekojs$onHorseContainerOpened(CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        AbstractContainerMenu menu = self.containerMenu;
        if (menu != null && menu != self.inventoryMenu) {
            if (!StageGateEnforcer.allowOpenContainer(self, menu)) {
                nekojs$closeBlockedContainer(self);
                return;
            }
            FabricPlayerEventBindings.postContainerOpened(self, menu);
        }
    }

    @Inject(method = "openNautilusInventory(Lnet/minecraft/world/entity/animal/nautilus/AbstractNautilus;Lnet/minecraft/world/Container;)V",
            at = @At("RETURN"))
    private void nekojs$onNautilusContainerOpened(CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        AbstractContainerMenu menu = self.containerMenu;
        if (menu != null && menu != self.inventoryMenu) {
            if (!StageGateEnforcer.allowOpenContainer(self, menu)) {
                nekojs$closeBlockedContainer(self);
                return;
            }
            FabricPlayerEventBindings.postContainerOpened(self, menu);
        }
    }

    @Inject(method = "doCloseContainer()V", at = @At("HEAD"), require = 1, allow = 1)
    private void nekojs$onContainerClosing(CallbackInfo ci) {
        if (!nekojs$closingBlockedContainer) {
            ServerPlayer self = (ServerPlayer) (Object) this;
            FabricPlayerEventBindings.postContainerClosed(self, self.containerMenu);
        }
    }

    @Inject(method = "doCloseContainer()V", at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD,
            target = "Lnet/minecraft/server/level/ServerPlayer;containerMenu:Lnet/minecraft/world/inventory/AbstractContainerMenu;"),
            require = 1, allow = 1)
    private void nekojs$onStableContainerClosed(CallbackInfo ci) {
        if (!nekojs$closingBlockedContainer) {
            ServerPlayer self = (ServerPlayer) (Object) this;
            FabricPlayerEventBindings.postStableContainerClosed(self, self.containerMenu);
        }
    }

    @Unique
    private void nekojs$closeBlockedContainer(ServerPlayer self) {
        nekojs$closingBlockedContainer = true;
        try {
            self.closeContainer();
        } finally {
            nekojs$closingBlockedContainer = false;
        }
    }
}
