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

// 1.21.1 side of McPlatformCompat.Impl: dist comes from the FMLEnvironment.dist field, the OP check
// from Player#hasPermissions(2), and bidirectional registration from the 3-argument
// playBidirectional (the handler dispatches on the flow).
// Registration: versions/1.21.1/src/main/resources/META-INF/services/McPlatformCompat$Impl.
// (The disabled state is expressed by the guard on the first line and the closing wrapper on the
//  last. Explain things inside the block with line comments only, never with a block doc comment:
//  its closing marker ends the outer disabled wrapper early and stonecutter reports Unclosed scope.)
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
