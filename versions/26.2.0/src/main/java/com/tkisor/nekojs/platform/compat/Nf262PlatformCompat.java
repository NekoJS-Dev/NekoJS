package com.tkisor.nekojs.platform.compat;

import com.tkisor.nekojs.dynamic.DynamicRegistryClientSync;
import com.tkisor.nekojs.dynamic.DynamicRegistrySyncWire;
import com.tkisor.nekojs.network.DynamicRegistrySyncPacket;
import com.tkisor.nekojs.network.NetworkMessageHandler;
import com.tkisor.nekojs.network.NekoScriptPayload;
import net.minecraft.commands.Commands;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 26.2 implementation of {@link McPlatformCompat.Impl}, with the same body as the 26.1 side; the
 * class name deliberately differs because the 26.1 to 26.2 drift comparison pair does not allow
 * the same name with a different body. Every payload, including the dynamic registration batch
 * transaction, registers in the same RegisterPayloadHandlersEvent; no other registration
 * channel exists.
 * Registered through {@code META-INF/services/McPlatformCompat$Impl}.
 */
public final class Nf262PlatformCompat implements McPlatformCompat.Impl {

    @Override
    public boolean isClientDist() {
        return FMLEnvironment.getDist() == Dist.CLIENT;
    }

    @Override
    public boolean isGameMaster(Player player) {
        return Commands.LEVEL_GAMEMASTERS.check(player.permissions());
    }

    @Override
    public void registerScriptPayload(PayloadRegistrar registrar) {
        registrar.playBidirectional(
                NekoScriptPayload.TYPE,
                NekoScriptPayload.CODEC,
                NetworkMessageHandler::handleScriptPayloadOnServer,
                NetworkMessageHandler::handleScriptPayloadOnClient
        );
    }

    @Override
    public void registerDynamicSyncPayload(PayloadRegistrar registrar) {
        registrar.commonBidirectional(
                DynamicRegistrySyncPacket.TYPE,
                DynamicRegistrySyncPacket.COMMON_CODEC,
                DynamicRegistrySyncWire::handleOnServer,
                DynamicRegistryClientSync::handleOnClient
        );
    }
}
