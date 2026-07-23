package com.tkisor.nekojs.listener;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.NekoJSMod;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

/**
 * 1.12.2 PlayerEventListener - adapted from neoforge-26.1 version.
 * Notifies server operators of script errors on login.
 */
@Mod.EventBusSubscriber(modid = NekoJS.MODID)
public class PlayerEventListener {

    private static final TextComponentString SCRIPT_OK = new TextComponentString(
            "[NekoJS] All scripts loaded successfully!");

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP player) {
            MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
            if (server != null && server.getPlayerList().canSendCommands(player.getGameProfile())) {
                if (NekoJSMod.RUNTIME_ROOT != null && NekoJSMod.RUNTIME_ROOT.errors().count() > 0) {
                    player.sendMessage(new TextComponentString(
                            "[NekoJS] There are script errors! Check the server console for details."));
                } else {
                    player.sendMessage(SCRIPT_OK);
                }
            }
        }
    }
}
