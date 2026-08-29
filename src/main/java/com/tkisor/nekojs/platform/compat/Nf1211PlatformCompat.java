//? if neoforge {
//? if <26 {
/*package com.tkisor.nekojs.platform.compat;

import com.tkisor.nekojs.network.NetworkMessageHandler;
import com.tkisor.nekojs.network.NekoScriptPayload;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

// 1.21.1 侧 McPlatformCompat.Impl：dist 走字段 FMLEnvironment.dist、
// OP 检查走 Player#hasPermissions(2)、双向注册走 3 参 playBidirectional
// （handler 内按 flow 分发）。
// 注册：versions/1.21.1/src/main/resources/META-INF/services/McPlatformCompat$Impl。
//（禁用态由首行守卫与末行收尾包装表示；块内说明一律用行注释,勿写块文档注释——
//  其闭合符会提前终结外层禁用包装,stonecutter 报 Unclosed scope。）
public final class Nf1211PlatformCompat implements McPlatformCompat.Impl {

    @Override
    public boolean isClientDist() {
        return FMLEnvironment.dist == Dist.CLIENT;
    }

    @Override
    public boolean isGameMaster(Player player) {
        return player.hasPermissions(2);
    }

    @Override
    public void registerScriptPayload(PayloadRegistrar registrar) {
        registrar.playBidirectional(NekoScriptPayload.TYPE, NekoScriptPayload.CODEC, (payload, context) -> {
            if (context.flow() == PacketFlow.SERVERBOUND) {
                NetworkMessageHandler.handleScriptPayloadOnServer(payload, context);
            } else {
                NetworkMessageHandler.handleScriptPayloadOnClient(payload, context);
            }
        });
    }
}
*///?}
//?}
