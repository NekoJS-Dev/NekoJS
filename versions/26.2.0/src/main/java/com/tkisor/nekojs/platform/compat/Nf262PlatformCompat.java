package com.tkisor.nekojs.platform.compat;

import com.tkisor.nekojs.network.NetworkMessageHandler;
import com.tkisor.nekojs.network.NekoScriptPayload;
import net.minecraft.commands.Commands;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 26.2 侧 {@link McPlatformCompat.Impl}：与 26.1 同体（类名刻意不同——26.1↔26.2 的
 * drift 比较对不允许同名不同体）。
 * 注册：{@code META-INF/services/McPlatformCompat$Impl}。
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
}
