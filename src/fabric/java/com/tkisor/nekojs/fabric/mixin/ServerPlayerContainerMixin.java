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
 * {@code PlayerEvents.containerOpened/containerClosed} 的 fabric 挂点（javap 实证
 * minecraft-merged-deobf-26.1.2.jar，字段 {@code containerMenu}/{@code inventoryMenu}
 * 声明于 {@code Player}，均为 public）。
 *
 * <p>注入策略：INVOKE 注入的 handler 参数只能取宿主方法参（前缀），
 * {@code openMenu} 新建的 {@code AbstractContainerMenu} 是局部变量、取不到，
 * 因此三个 open 方法全部用 {@code @At("RETURN")} + 读 {@code containerMenu}
 * （{@code putfield containerMenu} 都在 RETURN 之前：openMenu 偏移 109、
 * openHorseInventory 偏移 66、openNautilusInventory 偏移 66）。
 *
 * <p>守卫：{@code containerMenu != inventoryMenu} 排除“创世菜单失败/无菜单”
 * 路径（此时菜单回落到 inventoryMenu）；openMenu 额外用
 * {@code getReturnValue().isPresent()} 只在 {@code OptionalInt.of} 成功路触发。
 *
 * <p>{@code doCloseContainer()} 无参：HEAD 注入。此点 {@code containerMenu}
 * 仍是被关闭的旧菜单（{@code removed} 与 putfield 都在其后）；不取
 * {@code transferState} INVOKE 点是因为其 CP owner 是静态类型
 * {@code InventoryMenu}，方法实际声明于 {@code AbstractContainerMenu}，
 * 混合器 INVOKE 扫描对 owner≠声明类的调用点计数 0（本会话实证）。
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
