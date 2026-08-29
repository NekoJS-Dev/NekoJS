package com.tkisor.nekojs.platform.compat;

import com.tkisor.nekojs.network.NetworkMessageHandler;
import com.tkisor.nekojs.network.NekoScriptPayload;
import net.minecraft.commands.Commands;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 26.1 侧 {@link McPlatformCompat.Impl}：dist 走 {@code FMLEnvironment.getDist()}、
 * OP 检查走 {@code Commands.LEVEL_GAMEMASTERS.check}、双向注册走 4 参 playBidirectional
 * （分别指定两端 handler）。
 * 注册：{@code META-INF/services/McPlatformCompat$Impl}。
 */
public final class Nf261PlatformCompat implements McPlatformCompat.Impl {

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
}
