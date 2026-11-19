package com.tkisor.nekojs.mixin.stage;

import com.llamalad7.mixinextras.sugar.Local;
import com.tkisor.nekojs.stage.MinecraftStagePlatformHooks;
import com.tkisor.nekojs.stage.StageDomainOwner;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MixinRegionMovementStageGate {
    @Shadow public ServerPlayer player;

    @Inject(
            method = "handleMovePlayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;getBoundingBox()Lnet/minecraft/world/phys/AABB;",
                    ordinal = 0
            ),
            cancellable = true
    )
    private void nekojs$checkPlayerRegionMovement(
            ServerboundMovePlayerPacket packet,
            CallbackInfo ci,
            @Local(ordinal = 0) double x,
            @Local(ordinal = 1) double y,
            @Local(ordinal = 2) double z
    ) {
        if (packet.hasPosition() && StageDomainOwner.rejectRegionMovement(player, x, y, z)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "handleMoveVehicle",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;",
                    ordinal = 0
            ),
            cancellable = true
    )
    private void nekojs$checkVehicleRegionMovement(
            ServerboundMoveVehiclePacket packet,
            CallbackInfo ci,
            @Local Entity vehicle,
            @Local(ordinal = 3) double x,
            @Local(ordinal = 4) double y,
            @Local(ordinal = 5) double z,
            @Local(ordinal = 0) float yaw
    ) {
        float rotation = (float) Math.toRadians(vehicle.getYRot() - yaw);
        if (nekojs$rejectMountedPassengers(vehicle, new Vec3(x, y, z), rotation)) {
            if (player.getRootVehicle() == vehicle) {
                MinecraftStagePlatformHooks.sendVehicleCorrection(player, vehicle);
            }
            ci.cancel();
        }
    }

    @Unique
    private static boolean nekojs$rejectMountedPassengers(Entity vehicle, Vec3 destination, float rotation) {
        for (Entity passenger : vehicle.getPassengers()) {
            Vec3 seat = vehicle.getPassengerRidingPosition(passenger).subtract(vehicle.position());
            Vec3 target = destination.add(seat.yRot(rotation)).subtract(passenger.getVehicleAttachmentPoint(vehicle));
            if (passenger instanceof ServerPlayer rider
                    && StageDomainOwner.rejectRegionMovement(rider, target.x, target.y, target.z)) {
                return true;
            }
            if (nekojs$rejectMountedPassengers(passenger, target, 0.0F)) {
                return true;
            }
        }
        return false;
    }
}
