package com.tkisor.nekojs.fabric.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.tkisor.nekojs.fabric.event.FabricBlockEventBindingsV2;
import com.tkisor.nekojs.stage.StageGateEnforcer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

public final class MixinToolBlockModification {

    private static final String USE_ON = "useOn(Lnet/minecraft/world/item/context/UseOnContext;)"
            + "Lnet/minecraft/world/InteractionResult;";
    private static final String CAN_LIGHT = "canLight(Lnet/minecraft/world/level/block/state/BlockState;)Z";

    private MixinToolBlockModification() {}

    @Mixin(AxeItem.class)
    public static abstract class Axe {
        @ModifyExpressionValue(method = "evaluateNewBlockState(Lnet/minecraft/world/level/Level;"
                + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;"
                + "Lnet/minecraft/world/level/block/state/BlockState;)Ljava/util/Optional;",
                at = @At(value = "INVOKE", target = "Ljava/util/Optional;isPresent()Z"), require = 3, allow = 3)
        private boolean nekojs$allowAxeModification(boolean hasReplacement, Level level, BlockPos pos,
                                                    Player player, BlockState state) {
            return hasReplacement && (level.isClientSide()
                    || (StageGateEnforcer.allowToolModifyBlock(player, level, pos, state)
                    && !FabricBlockEventBindingsV2.postToolModification(level, pos, state, player)));
        }
    }

    @Mixin(ShovelItem.class)
    public static abstract class Shovel {
        @ModifyExpressionValue(method = USE_ON, at = {
                @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;isAir()Z"),
                @At(value = "INVOKE", target = "Ljava/lang/Boolean;booleanValue()Z")
        }, require = 2, allow = 2)
        private boolean nekojs$allowShovelModification(boolean eligible, UseOnContext context,
                                                        @Local(ordinal = 0) BlockState state) {
            if (!eligible || context.getLevel().isClientSide()) {
                return eligible;
            }
            Level level = context.getLevel();
            BlockPos pos = context.getClickedPos();
            Player player = context.getPlayer();
            return StageGateEnforcer.allowToolModifyBlock(player, level, pos, state)
                    && !FabricBlockEventBindingsV2.postToolModification(level, pos, state, player);
        }
    }

    @Mixin(HoeItem.class)
    public static abstract class Hoe {
        @ModifyExpressionValue(method = USE_ON, at = @At(value = "INVOKE",
                target = "Ljava/util/function/Predicate;test(Ljava/lang/Object;)Z"), require = 1, allow = 1)
        private boolean nekojs$allowTilling(boolean eligible, UseOnContext context) {
            if (!eligible || context.getLevel().isClientSide()) {
                return eligible;
            }
            Level level = context.getLevel();
            BlockPos pos = context.getClickedPos();
            Player player = context.getPlayer();
            BlockState state = level.getBlockState(pos);
            return StageGateEnforcer.allowToolModifyBlock(player, level, pos, state)
                    && !FabricBlockEventBindingsV2.postToolModification(level, pos, state, player);
        }
    }

    @Mixin(ShearsItem.class)
    public static abstract class Shears {
        @ModifyExpressionValue(method = USE_ON, at = @At(value = "INVOKE",
                target = "Lnet/minecraft/world/level/block/GrowingPlantHeadBlock;"
                        + "isMaxAge(Lnet/minecraft/world/level/block/state/BlockState;)Z"), require = 1, allow = 1)
        private boolean nekojs$skipTrimming(boolean maxAge, UseOnContext context, @Local BlockState state) {
            if (maxAge || context.getLevel().isClientSide()) {
                return maxAge;
            }
            Level level = context.getLevel();
            BlockPos pos = context.getClickedPos();
            Player player = context.getPlayer();
            return !StageGateEnforcer.allowToolModifyBlock(player, level, pos, state)
                    || FabricBlockEventBindingsV2.postToolModification(level, pos, state, player);
        }
    }

    @Mixin({FlintAndSteelItem.class, FireChargeItem.class})
    public static abstract class Igniters {
        @ModifyExpressionValue(method = USE_ON, at = {
                @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/CampfireBlock;" + CAN_LIGHT),
                @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/CandleBlock;" + CAN_LIGHT),
                @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/CandleCakeBlock;" + CAN_LIGHT)
        }, require = 3, allow = 3)
        private boolean nekojs$allowLighting(boolean canLight, UseOnContext context,
                                              @Local BlockState state, @Share("lightingDenied") LocalBooleanRef denied) {
            if (!canLight || context.getLevel().isClientSide()) {
                return canLight;
            }
            if (denied.get()) {
                return false;
            }
            Level level = context.getLevel();
            BlockPos pos = context.getClickedPos();
            Player player = context.getPlayer();
            boolean allowed = StageGateEnforcer.allowToolModifyBlock(player, level, pos, state)
                    && !FabricBlockEventBindingsV2.postToolModification(level, pos, state, player);
            denied.set(!allowed);
            return allowed;
        }
    }
}
